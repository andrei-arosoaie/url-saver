import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { SaveControl, type Reply, type Send } from "./SaveControl";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});
it("ignores an old status response after the user starts a new save", async () => {
  let resolveStatus!: (reply: Reply) => void;
  const send = vi.fn<Send>((action) =>
    action === "status"
      ? new Promise((resolve) => {
          resolveStatus = resolve;
        })
      : Promise.resolve({
          ok: false,
          progress: { phase: "sending", retry: 0 },
        }),
  );
  const capture = {
    url: "https://example.test/page",
    title: "Current",
    content: "<p>Current text</p>",
  };
  render(
    <SaveControl extract={() => capture} send={send} pageUrl={capture.url} />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
  await act(async () => {});
  await act(async () => {
    resolveStatus({ ok: true, jobId: "older-save" });
  });
  expect(screen.queryByText("Article saved.")).toBeNull();
  expect(screen.getByRole("status").textContent).toBe("Saving article…");
  expect(send.mock.calls.some(([action]) => action === "acknowledge")).toBe(
    false,
  );
});
it("restores the countdown, displays retry progress, disables duplicate clicks and acknowledges confirmed success", async () => {
  vi.useFakeTimers();
  const pageUrl = "https://example.test/restart";
  let reply: Reply = {
    ok: false,
    progress: {
      phase: "waiting",
      retry: 1,
      nextAttemptAt: Date.now() + 60_000,
    },
  };
  const send = vi.fn<Send>(async (action) =>
    action === "acknowledge" ? { ok: true } : reply,
  );
  const extract = vi.fn();
  render(<SaveControl extract={extract} send={send} pageUrl={pageUrl} />);
  await act(async () => {});
  expect(screen.getByRole("status").textContent).toContain(
    "60 seconds · retry 1 of 10",
  );
  expect(
    screen
      .getByRole("button", { name: "Know: Save article" })
      .hasAttribute("disabled"),
  ).toBe(true);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(10_000);
  });
  expect(screen.getByRole("status").textContent).toContain(
    "50 seconds · retry 1 of 10",
  );
  reply = { ok: false, progress: { phase: "sending", retry: 1 } };
  await act(async () => {
    await vi.advanceTimersByTimeAsync(1000);
  });
  expect(screen.getByRole("status").textContent).toBe(
    "Retrying save · 1 of 10.",
  );
  reply = { ok: true, jobId: "confirmed", message: "Link saved." };
  await act(async () => {
    await vi.advanceTimersByTimeAsync(1000);
  });
  expect(screen.getByRole("status").textContent).toBe("Link saved.");
  expect(send).toHaveBeenCalledWith("acknowledge", { url: pageUrl, title: "" });
  expect(extract).not.toHaveBeenCalled();
  expect(
    send.mock.calls.every(
      ([action]) => action === "status" || action === "acknowledge",
    ),
  ).toBe(true);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(300);
  });
  expect(
    screen.getByRole("button", { name: "Know: Save article" }).className,
  ).toContain("saved");
});
