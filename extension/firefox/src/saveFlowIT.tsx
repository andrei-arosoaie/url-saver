import {
  cleanup,
  configure,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { ArticleApi } from "./api";
import { SaveControl, type Send } from "./SaveControl";
import {
  SaveQueue,
  queueKey,
  type SaveJob,
  type QueueStorage,
} from "./saveQueue";
import { articleHandler } from "./messages";

const origin = import.meta.env.KNOW_IT_API_ORIGIN as string | undefined;
if (!origin || !/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(origin))
  throw new Error(
    "Integration tests require KNOW_IT_API_ORIGIN pointing to a disposable local backend. Never run against Render or Supabase.",
  );
const nativeFetch = globalThis.fetch.bind(globalThis);
configure({ asyncUtilTimeout: 5000 });
afterEach(cleanup);

function fixture(transport: typeof fetch) {
  const records: Record<string, SaveJob> = {};
  let now = Date.now();
  const storage: QueueStorage = {
    async get(key) {
      return records[key] ? structuredClone(records[key]) : undefined;
    },
    async set(key, value) {
      records[key] = structuredClone(value);
    },
    async remove(key) {
      delete records[key];
    },
    async all() {
      return structuredClone(records);
    },
  };
  const api = new ArticleApi(origin!, async () => undefined, transport);
  const alarms = { async schedule() {}, async clear() {} };
  const build = () => new SaveQueue(api, storage, alarms, () => now);
  let queue = build();
  const capture = {
    url: `https://example.test/phase3/${crypto.randomUUID()}`,
    title: "Phase 3 original capture",
    content: "<h2>Article</h2><p>Every word survives retries.</p>",
  };
  let handler = articleHandler(
    api,
    async () => ({ active: true, url: capture.url }),
    queue,
  );
  const send: Send = async (action, capture, id, version) =>
    (await handler(
      { type: "know:article", action, capture, id, version },
      { tab: { id: 1 }, frameId: 0 },
    ))!;
  return {
    capture,
    api,
    send,
    records,
    queue: () => queue,
    async attempt() {
      await queue.run(queueKey(capture.url));
    },
    advance() {
      now += 60_000;
    },
    restart() {
      queue = build();
      handler = articleHandler(
        api,
        async () => ({ active: true, url: capture.url }),
        queue,
      );
    },
    async saved() {
      const response = await nativeFetch(
        `${origin}/api/v1/articles?query=${encodeURIComponent(capture.url)}`,
      );
      expect(response.status).toBe(200);
      return (await response.json()).items as {
        id: string;
        url: string;
        version: number;
      }[];
    },
  };
}

it("recovers after non-response and restart through UI, background, real HTTP and PostgreSQL without duplicate rows", async () => {
  let offline = true;
  const request = vi.fn<typeof fetch>(async (...args) => {
    if (offline) throw new TypeError("Simulated lost connection");
    return nativeFetch(...args);
  });
  const f = fixture(request);
  let view = render(
    <SaveControl
      extract={() => f.capture}
      send={f.send}
      pageUrl={f.capture.url}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
  await waitFor(async () =>
    expect(await f.queue().status(f.capture.url)).toHaveProperty("progress"),
  );
  await f.attempt();
  await screen.findByText(/Server did not respond.*retry 1 of 10/);
  expect(
    screen
      .getByRole("button", { name: "Know: Save article" })
      .hasAttribute("disabled"),
  ).toBe(true);
  expect(await f.saved()).toHaveLength(0);
  view.unmount();
  f.restart();
  view = render(
    <SaveControl
      extract={() => ({ ...f.capture, title: "Changed after restart" })}
      send={f.send}
      pageUrl={f.capture.url}
    />,
  );
  await screen.findByText(/Server did not respond.*retry 1 of 10/);
  offline = false;
  f.advance();
  await f.attempt();
  await screen.findByText("Article saved.");
  const rows = await f.saved();
  expect(rows).toHaveLength(1);
  expect(await f.api.existing(rows[0]!.id)).toMatchObject({
    ...f.capture,
    version: 0,
  });
  expect(
    request.mock.calls.filter(([, options]) => options?.method === "POST"),
  ).toHaveLength(2);
  expect(f.records[queueKey(f.capture.url)]?.capture).toBeUndefined();
  view.unmount();
});

it("does not overwrite after the server commits but its response is lost, and restores duplicate review after restart", async () => {
  let loseResponse = true;
  const request = vi.fn<typeof fetch>(async (...args) => {
    const response = await nativeFetch(...args);
    if (loseResponse && args[1]?.method === "POST") {
      loseResponse = false;
      expect(response.status).toBe(201);
      throw new TypeError("Response lost after database commit");
    }
    return response;
  });
  const f = fixture(request);
  let view = render(
    <SaveControl
      extract={() => f.capture}
      send={f.send}
      pageUrl={f.capture.url}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
  await waitFor(async () =>
    expect(await f.queue().status(f.capture.url)).toHaveProperty("progress"),
  );
  await f.attempt();
  await screen.findByText(/Your save is not confirmed/);
  expect(screen.queryByText("Article saved.")).toBeNull();
  f.advance();
  await f.attempt();
  const update = await screen.findByRole("button", { name: "Update existing" });
  expect(update).toBeTruthy();
  view.unmount();
  f.restart();
  view = render(
    <SaveControl
      extract={() => f.capture}
      send={f.send}
      pageUrl={f.capture.url}
    />,
  );
  await screen.findByRole("button", { name: "Update existing" });
  const rows = await f.saved();
  expect(rows).toHaveLength(1);
  expect(await f.api.existing(rows[0]!.id)).toMatchObject({
    ...f.capture,
    version: 0,
  });
  expect(
    request.mock.calls.some(([, options]) => options?.method === "PATCH"),
  ).toBe(false);
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  await waitFor(() =>
    expect(f.records[queueKey(f.capture.url)]).toBeUndefined(),
  );
  view.unmount();
});

it("exhausts ten retries with clear final UI feedback and no article in PostgreSQL", async () => {
  const request = vi
    .fn<typeof fetch>()
    .mockRejectedValue(new TypeError("Offline"));
  const f = fixture(request);
  render(
    <SaveControl
      extract={() => f.capture}
      send={f.send}
      pageUrl={f.capture.url}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
  await waitFor(async () =>
    expect(await f.queue().status(f.capture.url)).toHaveProperty("progress"),
  );
  for (let i = 0; i < 11; i++) {
    await f.attempt();
    f.advance();
  }
  expect((await screen.findByRole("alert")).textContent).toContain(
    "after 10 retries",
  );
  expect(
    screen.getByRole("button", { name: "Know: Save article" }).className,
  ).toContain("error");
  expect(request).toHaveBeenCalledTimes(11);
  expect(await f.saved()).toHaveLength(0);
  expect(f.records[queueKey(f.capture.url)]?.capture).toBeUndefined();
});

it("shows a real backend validation error without retrying or persisting an invalid article", async () => {
  const request = vi.fn<typeof fetch>((...args) => nativeFetch(...args));
  const f = fixture(request);
  f.capture.title = " ";
  render(
    <SaveControl
      extract={() => f.capture}
      send={f.send}
      pageUrl={f.capture.url}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
  await waitFor(async () =>
    expect(await f.queue().status(f.capture.url)).toHaveProperty("progress"),
  );
  await f.attempt();
  await screen.findByRole("alert");
  f.advance();
  await f.attempt();
  expect(request).toHaveBeenCalledTimes(1);
  expect(await f.queue().status(f.capture.url)).toMatchObject({ status: 400 });
  expect(await f.saved()).toHaveLength(0);
});
