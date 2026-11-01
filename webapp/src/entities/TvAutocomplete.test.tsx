import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { useState } from "react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { TvAutocomplete } from "./TvAutocomplete";
import type { Candidate } from "./tvApi";

const candidates = Array.from({ length: 12 }, (_, i) => ({
  tvmazeId: i + 1,
  name: "Same name",
  premiereYear: 2000 + i,
  posterUrl: null,
  providerStatus: "Ended",
}));
function Harness() {
  const [value, setValue] = useState(""),
    [selected, setSelected] = useState<Candidate | null>(null);
  return (
    <>
      <TvAutocomplete
        value={value}
        onChange={setValue}
        selected={selected}
        onSelect={setSelected}
      />
      <output aria-label="Chosen ID">{selected?.tvmazeId}</output>
    </>
  );
}
beforeEach(() => {
  vi.useFakeTimers();
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
function stub(
  fetcher: (...args: Parameters<typeof fetch>) => Promise<Response>,
) {
  vi.stubGlobal("fetch", (input: RequestInfo | URL, init?: RequestInit) =>
    String(input).endsWith("/settings")
      ? Promise.resolve(
          new Response(
            JSON.stringify({
              minimumCharacters: 2,
              debounceMs: 300,
              pageSize: 8,
            }),
          ),
        )
      : fetcher(input, init),
  );
}
const response = (items = candidates) =>
  new Response(
    JSON.stringify({
      items,
      state: "FRESH",
      matchStatus: "NEEDS_SELECTION",
      minimumCharacters: 2,
      debounceMs: 300,
      pageSize: 8,
    }),
  );
it("debounces at two characters, exposes all matches and selects stable IDs by keyboard", async () => {
  const fetcher = vi.fn().mockResolvedValue(response());
  stub(fetcher);
  render(<Harness />);
  await act(async () => {});
  const input = screen.getByRole("combobox");
  fireEvent.change(input, { target: { value: "s" } });
  await act(() => vi.advanceTimersByTimeAsync(500));
  expect(fetcher).not.toHaveBeenCalled();
  fireEvent.change(input, { target: { value: "sa" } });
  await act(() => vi.advanceTimersByTimeAsync(299));
  expect(fetcher).not.toHaveBeenCalled();
  await act(() => vi.advanceTimersByTimeAsync(1));
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(screen.getAllByRole("option")).toHaveLength(8);
  fireEvent.click(screen.getByRole("button", { name: "More matches" }));
  expect(screen.getAllByRole("option")).toHaveLength(12);
  fireEvent.keyDown(input, { key: "ArrowDown" });
  fireEvent.keyDown(input, { key: "ArrowDown" });
  fireEvent.keyDown(input, { key: "Enter" });
  expect(screen.getByLabelText("Chosen ID").textContent).toBe("2");
  expect(screen.queryByRole("listbox")).toBeNull();
});
it("cancels obsolete requests and ignores late responses", async () => {
  let late!: (value: Response) => void;
  const fetcher = vi
    .fn()
    .mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          late = resolve;
        }),
    )
    .mockResolvedValueOnce(
      response([{ ...candidates[0], name: "Current show" }]),
    );
  stub(fetcher);
  render(<Harness />);
  await act(async () => {});
  const input = screen.getByRole("combobox");
  fireEvent.change(input, { target: { value: "old" } });
  await act(() => vi.advanceTimersByTimeAsync(300));
  const oldSignal = (fetcher.mock.calls[0][1] as RequestInit).signal!;
  fireEvent.change(input, { target: { value: "current" } });
  await act(() => vi.advanceTimersByTimeAsync(300));
  expect(oldSignal.aborted).toBe(true);
  await act(async () => {
    late(response());
  });
  expect(screen.getAllByRole("option")).toHaveLength(1);
  expect(screen.getByRole("option").textContent).toContain("Current show");
});
it("retains manual entry on empty results and provider failure", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(response([]))
    .mockRejectedValueOnce(new Error("offline"));
  stub(fetcher);
  render(<Harness />);
  await act(async () => {});
  const input = screen.getByRole("combobox") as HTMLInputElement;
  fireEvent.change(input, { target: { value: "no matches" } });
  await act(() => vi.advanceTimersByTimeAsync(300));
  expect(screen.getAllByRole("status")[0].textContent).toContain("No matches");
  fireEvent.change(input, { target: { value: "manual title" } });
  await act(() => vi.advanceTimersByTimeAsync(300));
  expect(screen.getAllByRole("status")[0].textContent).toContain(
    "Search unavailable",
  );
  expect(input.value).toBe("manual title");
});
