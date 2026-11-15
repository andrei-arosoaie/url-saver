import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { AccountControl } from "./AccountControl";
const send = vi.hoisted(() => vi.fn());
vi.mock("wxt/browser", () => ({ browser: { runtime: { sendMessage: send } } }));
beforeEach(() => {
  send.mockReset();
});
afterEach(cleanup);
test("a failed initial status check offers a real retry instead of remaining stuck", async () => {
  send
    .mockRejectedValueOnce(new Error("Background unavailable"))
    .mockResolvedValue({ signedIn: false, signingIn: false });
  render(<AccountControl />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Retry connection" }),
  );
  await screen.findByRole("button", { name: "Sign in with Google" });
  expect(send.mock.calls.map(([message]) => message.action)).toEqual([
    "status",
    "status",
  ]);
});
test("starting Android login does not falsely report successful authentication", async () => {
  send.mockImplementation(async ({ action }: { action: string }) =>
    action === "signIn"
      ? { ok: true, signedIn: false, signingIn: true }
      : { signedIn: false, signingIn: false },
  );
  render(<AccountControl />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Sign in with Google" }),
  );
  await waitFor(() =>
    expect(send).toHaveBeenCalledWith({
      type: "know:identity",
      action: "signIn",
    }),
  );
  expect(screen.queryByText("You are signed in.")).toBeNull();
});
test("reopening the popup recognizes a persisted pending login", async () => {
  send.mockResolvedValue({ signedIn: false, signingIn: true });
  render(<AccountControl />);
  await screen.findByRole("button", { name: "Return to sign-in" });
  expect(
    screen.getByText("Sign-in is in progress in another Firefox tab."),
  ).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Sign out" })).toBeNull();
});
