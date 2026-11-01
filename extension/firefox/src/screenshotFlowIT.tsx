import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";

import { ArticleApi } from "./api";
import { articleHandler } from "./messages";
import { SaveControl, type Send } from "./SaveControl";
import { SaveQueue, queueKey, type SaveJob } from "./saveQueue";
import {
  ScreenshotQueue,
  screenshotKey,
  type ScreenshotJob,
} from "./screenshotQueue";

const origin = import.meta.env.KNOW_IT_API_ORIGIN as string;
const provider = import.meta.env.KNOW_IT_IMAGE_ORIGIN as string;
for (const value of [origin, provider])
  if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(value ?? ""))
    throw new Error(
      "Screenshot integration tests require disposable local backend/provider endpoints.",
    );
afterEach(cleanup);

// Lossless 32 x 16 RGB viewport fixture, with valid PNG chunk checksums.
const nativePng = () =>
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAQCAIAAAD4YuoOAAAAG0lEQVR4nGNooDFgGLVg1IJRC0YtGLVgKFgAAI54AC7f1vaqAAAAAElFTkSuQmCC";

it("saves through UI/background/HTTP/PostgreSQL, retries the original image after restart, reads private JPEG, and replaces only after explicit confirmation", async () => {
  const articleJobs: Record<string, SaveJob> = {},
    imageJobs: Record<string, ScreenshotJob> = {};
  let now = Date.now();
  const storage = <T,>(records: Record<string, T>) => ({
    async get(key: string) {
      return records[key] && structuredClone(records[key]);
    },
    async set(key: string, value: T) {
      records[key] = structuredClone(value);
    },
    async remove(key: string) {
      delete records[key];
    },
    async all() {
      return structuredClone(records);
    },
  });
  const request = vi.fn<typeof fetch>((...args) => globalThis.fetch(...args));
  const api = new ArticleApi(
    origin,
    async () => import.meta.env.KNOW_IT_ACCESS_TOKEN,
    request,
  );
  const capture = {
    url: `https://example.test/phase4/${crypto.randomUUID()}`,
    title: "Native viewport",
    content: "<p>Article survives image failure.</p>",
  };
  const viewport = vi.fn().mockResolvedValue(nativePng());
  const alarms = { async schedule() {}, async clear() {} };
  let images: ScreenshotQueue, queue: SaveQueue, send: Send;
  const restart = () => {
    images = new ScreenshotQueue(api, storage(imageJobs), alarms, () => now);
    queue = new SaveQueue(api, storage(articleJobs), alarms, () => now, images);
    const handler = articleHandler(
      api,
      async () => ({ active: true, url: capture.url }),
      queue,
      viewport,
    );
    send = async (action, value, id, version) =>
      (await handler(
        { type: "know:article", action, capture: value, id, version },
        { tab: { id: 1 }, frameId: 0 },
      ))!;
  };
  restart();
  let view = render(
    <SaveControl extract={() => capture} send={send!} pageUrl={capture.url} />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
  await waitFor(() => expect(articleJobs[queueKey(capture.url)]).toBeDefined());
  await queue!.run(queueKey(capture.url));
  const listing = await (
    await fetch(
      `${origin}/api/v1/articles?query=${encodeURIComponent(capture.url)}`,
    )
  ).json();
  expect(listing.items).toHaveLength(1);
  const id = listing.items[0].id;
  await fetch(`${provider}/__test/fail-next`);
  await images!.run(screenshotKey(capture.url));
  expect(imageJobs[screenshotKey(capture.url)]?.phase).toBe("waiting");
  await screen.findByText(/Article saved. Screenshot upload will retry/);
  fireEvent.click(screen.getByRole("button", { name: "Dismiss Know message" }));
  expect(
    screen.queryByRole("button", { name: "Dismiss Know message" }),
  ).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
  await fetch(`${provider}/__test/fail-next`);
  fireEvent.click(screen.getByRole("button", { name: "Retry screenshot now" }));
  await waitFor(() =>
    expect(imageJobs[screenshotKey(capture.url)]?.nextAt).toBeLessThanOrEqual(
      now,
    ),
  );
  await images!.run(screenshotKey(capture.url));
  expect(imageJobs[screenshotKey(capture.url)]?.attempts).toBe(2);
  const original = imageJobs[screenshotKey(capture.url)]!;
  expect(original.dataUrl).toBe(
    viewport.mock.results[0]!.value instanceof Promise
      ? await viewport.mock.results[0]!.value
      : viewport.mock.results[0]!.value,
  );
  view.unmount();
  restart();
  now += 60000;
  view = render(
    <SaveControl extract={() => capture} send={send!} pageUrl={capture.url} />,
  );
  await images!.run(screenshotKey(capture.url));
  await screen.findByText("Article and screenshot saved.");
  const metadata = await (
    await fetch(`${origin}/api/v1/articles/${id}/screenshot`)
  ).json();
  expect(metadata).toMatchObject({
    status: "AVAILABLE",
    width: 32,
    height: 16,
    contentType: "image/jpeg",
    version: 1,
  });
  const download = await fetch(metadata.url);
  expect(download.status).toBe(200);
  expect([...new Uint8Array(await download.arrayBuffer()).slice(0, 2)]).toEqual(
    [255, 216],
  );
  const article = await api.existing(id);
  expect(article.content).toBe(capture.content);
  expect(article.version).toBe(0);
  expect(viewport).toHaveBeenCalledTimes(1);
  expect(
    request.mock.calls.filter(([, init]) => init?.method === "POST"),
  ).toHaveLength(1);
  await waitFor(() =>
    expect(articleJobs[queueKey(capture.url)]).toBeUndefined(),
  );
  fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
  await waitFor(() => expect(articleJobs[queueKey(capture.url)]).toBeDefined());
  await queue!.run(queueKey(capture.url));
  await screen.findByRole("button", { name: "Update existing" });
  expect(
    (await (await fetch(`${origin}/api/v1/articles/${id}/screenshot`)).json())
      .version,
  ).toBe(1);
  fireEvent.click(screen.getByRole("button", { name: "Update existing" }));
  await waitFor(() =>
    expect(imageJobs[screenshotKey(capture.url)]?.phase).toBe("waiting"),
  );
  await images!.run(screenshotKey(capture.url));
  await screen.findByText("Article and screenshot saved.");
  expect(
    (await (await fetch(`${origin}/api/v1/articles/${id}/screenshot`)).json())
      .version,
  ).toBe(2);
  const rows = await (
    await fetch(
      `${origin}/api/v1/articles?query=${encodeURIComponent(capture.url)}`,
    )
  ).json();
  expect(rows.items).toHaveLength(1);
  expect((await api.existing(id)).content).toBe(capture.content);
  const remote = await (await fetch(`${provider}/__test/files`)).json();
  expect(remote.length).toBeGreaterThan(0);
  expect(remote.every((f: { privateFile: boolean }) => f.privateFile)).toBe(
    true,
  );
  view.unmount();
});
