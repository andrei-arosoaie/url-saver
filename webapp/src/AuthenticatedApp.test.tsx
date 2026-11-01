import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
const auth = vi.hoisted(() => ({
  initialize: vi.fn(),
  signIn: vi.fn(),
  signOut: vi.fn(),
}));
vi.mock("./auth", () => ({
  initializeAuthentication: auth.initialize,
  beginSignIn: auth.signIn,
  signOut: auth.signOut,
}));
vi.mock("./App", () => ({ App: () => <div>Private library</div> }));
beforeEach(() => {
  vi.resetModules();
  auth.initialize.mockReset();
  auth.signIn.mockReset();
  auth.signOut.mockReset();
});
afterEach(cleanup);
test("requires sign-in before mounting any library UI", async () => {
  auth.initialize.mockResolvedValue(false);
  auth.signIn.mockResolvedValue(undefined);
  const { AuthenticatedApp } = await import("./AuthenticatedApp");
  render(<AuthenticatedApp />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Sign in with Google" }),
  );
  expect(auth.signIn).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("Private library")).toBeNull();
});
test("confirmed logout removes the private library from the page", async () => {
  auth.initialize.mockResolvedValue(true);
  auth.signOut.mockResolvedValue(undefined);
  const { AuthenticatedApp } = await import("./AuthenticatedApp");
  render(<AuthenticatedApp />);
  await screen.findByText("Private library");
  fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
  await screen.findByRole("button", { name: "Sign in with Google" });
  expect(screen.queryByText("Private library")).toBeNull();
});
