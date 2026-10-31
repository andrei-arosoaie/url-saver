import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { RelatedArticles } from "./RelatedArticles";
import type { ConnectionsApi, ConnectionPage } from "./api";
import type { Article } from "../api";
import { complete, related, sourceId } from "./fixtures";
const article: Article = {
  id: sourceId,
  title: "Original article",
  content: "<p>Text</p>",
  category: "BOOKS",
  entityName: null,
  version: 0,
  savedAt: related.savedAt,
  url: "https://example.test/original",
};
function fixture() {
  return {
    list: vi.fn<ConnectionsApi["list"]>().mockResolvedValue(complete),
    retry: vi.fn<ConnectionsApi["retry"]>().mockResolvedValue(undefined),
    remove: vi.fn<ConnectionsApi["remove"]>().mockResolvedValue(undefined),
  };
}
const flush = () =>
  act(async () => {
    await Promise.resolve();
  });

it("explains a configuration pause without claiming that the provider quota was reached", async () => {
  const api = fixture();
  api.list.mockResolvedValue({
    ...complete,
    processing: {
      ...complete.processing,
      state: "PAUSED",
      reason: "LIVE_REQUESTS_DISABLED",
      nextAttemptAt: null,
    },
  });
  render(<RelatedArticles article={article} api={api} onOpen={vi.fn()} />);
  await screen.findByText(
    "Semantic matching is disabled on the server. Shared-entity results remain available.",
  );
  expect(screen.queryByText(/Provider quota reached/)).toBeNull();
  expect(screen.queryByText(/Next attempt:/)).toBeNull();
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
});
it("polls at the server interval, merges reasons into one article and stops at completion and unmount", async () => {
  vi.useFakeTimers();
  const api = fixture();
  api.list.mockResolvedValueOnce({
    ...complete,
    processing: {
      ...complete.processing,
      state: "PROCESSING",
      pollIntervalMs: 7500,
    },
  });
  const open = vi.fn();
  const view = render(
    <RelatedArticles article={article} api={api} onOpen={open} />,
  );
  await flush();
  expect(screen.getByText("Finding related articles…")).toBeTruthy();
  expect(screen.getAllByRole("link", { name: "Dune" })).toHaveLength(1);
  expect(screen.getByText("Both mention Dune")).toBeTruthy();
  expect(screen.getByText("Similar content")).toBeTruthy();
  expect(screen.queryByText("Captured title")).toBeNull();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(7499);
  });
  expect(api.list).toHaveBeenCalledTimes(1);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(1);
  });
  expect(api.list).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByRole("link", { name: "Dune" }));
  expect(open).toHaveBeenCalledWith(related.id);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(30000);
  });
  expect(api.list).toHaveBeenCalledTimes(2);
  view.unmount();
  expect(api.list.mock.calls[1][2].aborted).toBe(true);
  expect(api.retry).not.toHaveBeenCalled();
});
it("shows quota pause with partial results and resumes only through safe polling", async () => {
  vi.useFakeTimers();
  const api = fixture();
  api.list.mockResolvedValueOnce({
    ...complete,
    processing: {
      ...complete.processing,
      state: "PAUSED",
      nextAttemptAt: "2026-11-01T10:00:00Z",
    },
  });
  render(<RelatedArticles article={article} api={api} onOpen={vi.fn()} />);
  await flush();
  expect(screen.getByText(/Provider quota reached/)).toBeTruthy();
  expect(screen.getByRole("link", { name: "Dune" })).toBeTruthy();
  expect(
    screen.getByText(/Next attempt:/).querySelector("time")?.dateTime,
  ).toBe("2026-11-01T10:00:00Z");
  expect(screen.queryByRole("button", { name: "Retry matching" })).toBeNull();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(10000);
  });
  expect(screen.getByText("Matching complete for this article.")).toBeTruthy();
});
it("loads every page and refreshes all loaded pages without duplicates or dropping later results", async () => {
  const api = fixture();
  const second = {
    ...related,
    id: "00000000-0000-4000-8000-000000000005",
    entityName: null,
    title: "Second article",
  };
  api.list.mockImplementation(async (_id, cursor) =>
    cursor
      ? { ...complete, items: [second], nextCursor: null }
      : { ...complete, nextCursor: "next" },
  );
  render(<RelatedArticles article={article} api={api} onOpen={vi.fn()} />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Load more related articles" }),
  );
  await screen.findByRole("link", { name: "Second article" });
  fireEvent.click(screen.getByRole("button", { name: "Refresh connections" }));
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(4));
  expect(screen.getAllByRole("link")).toHaveLength(2);
});
it("requires explicit confirmation and removes a connection only after the server confirms", async () => {
  const api = fixture();
  api.remove.mockImplementation(async () => {
    api.list.mockResolvedValue({ ...complete, items: [] });
  });
  render(<RelatedArticles article={article} api={api} onOpen={vi.fn()} />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Remove connection to Dune" }),
  );
  expect(api.remove).not.toHaveBeenCalled();
  expect(screen.getByText(/Both articles stay saved/)).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(api.remove).not.toHaveBeenCalled();
  fireEvent.click(
    screen.getByRole("button", { name: "Remove connection to Dune" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Confirm removal" }));
  await screen.findByText(/Connection removed from both articles/);
  expect(api.remove).toHaveBeenCalledWith(
    sourceId,
    related.id,
    expect.any(AbortSignal),
  );
  expect(screen.queryByRole("link", { name: "Dune" })).toBeNull();
});
it("preserves failed and uncertain mutations until the user refreshes and never retries writes automatically", async () => {
  const api = fixture();
  api.remove.mockRejectedValue(
    new Error(
      "Removal was not confirmed. Refresh connections before trying again.",
    ),
  );
  render(<RelatedArticles article={article} api={api} onOpen={vi.fn()} />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Remove connection to Dune" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Confirm removal" }));
  await screen.findByRole("alert");
  await waitFor(() =>
    expect(
      screen
        .getByRole("button", { name: "Refresh connections" })
        .hasAttribute("disabled"),
    ).toBe(false),
  );
  expect(
    screen
      .getByRole("button", { name: "Confirm removal" })
      .hasAttribute("disabled"),
  ).toBe(true);
  expect(screen.getByRole("link", { name: "Dune" })).toBeTruthy();
  expect(api.remove).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "Refresh connections" }));
  await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
  expect(api.remove).toHaveBeenCalledTimes(1);
});
it("requires manual Retry after processing failure and preserves results while new work is queued", async () => {
  const api = fixture();
  api.list.mockResolvedValue({
    ...complete,
    processing: { ...complete.processing, state: "FAILED" },
  });
  api.retry.mockImplementation(async () => {
    api.list.mockResolvedValue({
      ...complete,
      processing: { ...complete.processing, state: "QUEUED" },
    });
  });
  render(<RelatedArticles article={article} api={api} onOpen={vi.fn()} />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Retry matching" }),
  );
  await screen.findByText("Finding related articles — queued.");
  expect(api.retry).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("link", { name: "Dune" })).toBeTruthy();
});
it("aborts stale refreshes during removal so a delayed read cannot resurrect the removed card", async () => {
  const api = fixture();
  let resolve: (value: ConnectionPage) => void = () => {};
  render(<RelatedArticles article={article} api={api} onOpen={vi.fn()} />);
  await screen.findByRole("link", { name: "Dune" });
  api.list.mockImplementationOnce(
    () =>
      new Promise((value) => {
        resolve = value;
      }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Refresh connections" }));
  fireEvent.click(
    screen.getByRole("button", { name: "Remove connection to Dune" }),
  );
  api.list.mockResolvedValue({ ...complete, items: [] });
  fireEvent.click(screen.getByRole("button", { name: "Confirm removal" }));
  await screen.findByText(/Connection removed from both articles/);
  await act(async () => resolve(complete));
  expect(screen.queryByRole("link", { name: "Dune" })).toBeNull();
});
it("stops hidden-page polling and reloads on return; leaving the article aborts pending requests", async () => {
  vi.useFakeTimers();
  const api = fixture();
  api.list.mockResolvedValue({
    ...complete,
    processing: { ...complete.processing, state: "QUEUED" },
  });
  const visible = vi
    .spyOn(document, "visibilityState", "get")
    .mockReturnValue("visible");
  const view = render(
    <RelatedArticles article={article} api={api} onOpen={vi.fn()} />,
  );
  await flush();
  visible.mockReturnValue("hidden");
  fireEvent(document, new Event("visibilitychange"));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(20000);
  });
  expect(api.list).toHaveBeenCalledTimes(1);
  visible.mockReturnValue("visible");
  fireEvent(document, new Event("visibilitychange"));
  await flush();
  expect(api.list).toHaveBeenCalledTimes(2);
  view.unmount();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(20000);
  });
  expect(api.list).toHaveBeenCalledTimes(2);
});
it("shows link-only and disabled states without polling, with no fabricated results", async () => {
  const api = fixture();
  api.list.mockResolvedValue({
    ...complete,
    items: [],
    processing: { ...complete.processing, state: "SKIPPED" },
  });
  render(
    <RelatedArticles
      article={{ ...article, content: null }}
      api={api}
      onOpen={vi.fn()}
    />,
  );
  await screen.findByText("Connections need saved article text.");
  expect(
    within(
      screen.getByRole("region", { name: "Related articles" }),
    ).queryByRole("link"),
  ).toBeNull();
});
