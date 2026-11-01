import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { usePage } from "./usePage";
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
});
test("background refresh updates all loaded pages without collapsing pagination", async () => {
  vi.useFakeTimers();
  vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
  let released = 2;
  const loader = vi.fn(async (cursor: string | null) => ({
    items: [{ id: cursor ? "second" : "first", airedCount: released }],
    nextCursor: cursor ? null : "page-2",
  }));
  const { result } = renderHook(() => usePage(loader, 60_000));
  await act(async () => {});
  await act(async () => {
    result.current.more();
  });
  expect(result.current.items).toHaveLength(2);
  released = 3;
  await act(async () => {
    await vi.advanceTimersByTimeAsync(60_000);
  });
  expect(result.current.items).toEqual([
    { id: "first", airedCount: 3 },
    { id: "second", airedCount: 3 },
  ]);
  expect(result.current.cursor).toBeNull();
  expect(loader).toHaveBeenCalledTimes(4);
});
