import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { IdentificationPanel } from "./IdentificationPanel";
import type { Identification, IdentificationApi } from "./api";
import type { Article } from "../api";
const article: Article = {
  id: "a",
  url: "https://example.test",
  title: "Article",
  content: "<p>Article</p>",
  savedAt: "2026-10-31T00:00:00Z",
  version: 0,
  category: "OTHER",
  entityName: null,
};
const state: Identification = {
  status: "NOT_REQUESTED",
  reason: null,
  provider: null,
  model: null,
  subjects: [],
  categorySource: "DEFAULT",
  nameSource: "DEFAULT",
  requestedAt: null,
  completedAt: null,
};
const done: Identification = {
  ...state,
  status: "SUCCEEDED",
  provider: "groq",
  model: "openai/gpt-oss-120b",
  subjects: [
    { name: "1984", main: true },
    { name: "Animal Farm", main: false },
  ],
  categorySource: "AI",
  nameSource: "AI",
  requestedAt: "2026-10-31T00:00:00Z",
  completedAt: "2026-10-31T00:00:01Z",
};
function fixture() {
  return {
    status: vi.fn<IdentificationApi["status"]>().mockResolvedValue(state),
    request: vi
      .fn<IdentificationApi["request"]>()
      .mockResolvedValue({ ...state, status: "PENDING" }),
  };
}
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});
it("polls active jobs, refreshes article details once, and shows multiple subjects with one main", async () => {
  vi.useFakeTimers();
  const api = fixture();
  api.status
    .mockResolvedValueOnce({ ...state, status: "RUNNING" })
    .mockResolvedValue(done);
  const finished = vi.fn().mockResolvedValue(undefined);
  render(
    <IdentificationPanel article={article} api={api} onFinished={finished} />,
  );
  await act(async () => {
    await Promise.resolve();
  });
  expect(screen.getByText("Identifying missing details…")).toBeTruthy();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(2500);
  });
  expect(screen.getByText("1984")).toBeTruthy();
  expect(screen.getByText("Animal Farm")).toBeTruthy();
  expect(screen.getAllByText("Main subject")).toHaveLength(1);
  expect(finished).toHaveBeenCalledTimes(1);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(10000);
  });
  expect(api.status).toHaveBeenCalledTimes(2);
  expect(api.request).not.toHaveBeenCalled();
});
it("requires explicit retry after both providers fail", async () => {
  const api = fixture();
  api.status.mockResolvedValue({
    ...state,
    status: "FAILED",
    reason: "PROVIDERS_UNAVAILABLE",
  });
  render(
    <IdentificationPanel
      article={article}
      api={api}
      onFinished={vi.fn().mockResolvedValue(undefined)}
    />,
  );
  const button = await screen.findByRole("button", {
    name: "Retry identification",
  });
  expect(api.request).not.toHaveBeenCalled();
  api.status.mockResolvedValue({ ...state, status: "PENDING" });
  fireEvent.click(button);
  await screen.findByRole("button", { name: "Identification in progress" });
  expect(api.request).toHaveBeenCalledTimes(1);
});
it("keeps an uncertain POST disabled until the user refreshes status", async () => {
  const api = fixture();
  api.request.mockRejectedValue(
    new Error("Request not confirmed. Refresh status before trying again."),
  );
  render(
    <IdentificationPanel
      article={article}
      api={api}
      onFinished={vi.fn().mockResolvedValue(undefined)}
    />,
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Identify missing details" }),
  );
  await screen.findByRole("alert");
  expect(
    (
      screen.getByRole("button", {
        name: "Identify missing details",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  api.status.mockResolvedValue({ ...state, status: "RUNNING" });
  fireEvent.click(screen.getByRole("button", { name: "Refresh status" }));
  await screen.findByText("Identifying missing details…");
  expect(api.request).toHaveBeenCalledTimes(1);
});
it("shows manual provenance and offers no AI action when both fields are manual or text is absent", async () => {
  const api = fixture();
  api.status.mockResolvedValue({
    ...state,
    status: "SKIPPED",
    reason: "NO_MISSING_FIELDS",
    categorySource: "USER",
    nameSource: "USER",
  });
  const { unmount } = render(
    <IdentificationPanel
      article={article}
      api={api}
      onFinished={vi.fn().mockResolvedValue(undefined)}
    />,
  );
  await screen.findByText("Your choices are preserved. No AI needed.");
  expect(screen.getAllByText("Your choice")).toHaveLength(2);
  expect(screen.queryByRole("button")).toBeNull();
  unmount();
  api.status.mockResolvedValue({
    ...state,
    status: "SKIPPED",
    reason: "NO_ARTICLE_TEXT",
  });
  render(
    <IdentificationPanel
      article={{ ...article, content: null }}
      api={api}
      onFinished={vi.fn().mockResolvedValue(undefined)}
    />,
  );
  await screen.findByText("No article text available for identification.");
  expect(screen.queryByRole("button")).toBeNull();
});
it("aborts requests on unmount and never applies a late completion to another article", async () => {
  const api = fixture();
  let resolve!: (value: Identification) => void;
  api.status.mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  const finished = vi.fn();
  const { unmount } = render(
    <IdentificationPanel article={article} api={api} onFinished={finished} />,
  );
  await waitFor(() => expect(api.status).toHaveBeenCalledTimes(1));
  const signal = api.status.mock.calls[0][1];
  unmount();
  await act(async () => {
    resolve(done);
    await Promise.resolve();
  });
  expect(signal.aborted).toBe(true);
  expect(finished).not.toHaveBeenCalled();
});
