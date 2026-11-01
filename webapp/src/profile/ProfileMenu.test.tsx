import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { ProfileMenu } from "./ProfileMenu";
import type { ProfileApi } from "./api";
afterEach(cleanup);
const profile = {
  displayName: "Andrei Example",
  pictureUrl: "https://lh3.googleusercontent.com/photo",
  pictureRemoved: false,
};
test("shows the Google photo and exposes sign-out inside the account menu", async () => {
  const logout = vi.fn(async () => {});
  const api: ProfileApi = { get: async () => profile, removePicture: vi.fn() };
  render(<ProfileMenu api={api} onSignOut={logout} />);
  const menu = await screen.findByLabelText("Account menu for Andrei Example");
  expect(menu.querySelector("img")?.src).toBe(profile.pictureUrl);
  expect(menu.closest("details")?.open).toBe(false);
  fireEvent.click(menu);
  expect(menu.closest("details")?.open).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
  await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
});
test("removing the photo waits for server confirmation and keeps initials plus sign-out", async () => {
  const remove = vi.fn(async () => ({
    ...profile,
    pictureUrl: null,
    pictureRemoved: true,
  }));
  render(
    <ProfileMenu
      api={{ get: async () => profile, removePicture: remove }}
      onSignOut={async () => {}}
    />,
  );
  const menu = await screen.findByLabelText("Account menu for Andrei Example");
  fireEvent.click(menu);
  fireEvent.click(screen.getByRole("button", { name: "Remove profile photo" }));
  await waitFor(() => expect(menu.querySelector("img")).toBeNull());
  expect(remove).toHaveBeenCalledTimes(1);
  expect(menu.textContent).toBe("AE");
  expect(screen.getByRole("button", { name: "Sign out" })).toBeTruthy();
  expect(
    screen.queryByRole("button", { name: "Remove profile photo" }),
  ).toBeNull();
});
test("a failed removal preserves the photo and displays an actionable error", async () => {
  render(
    <ProfileMenu
      api={{
        get: async () => profile,
        removePicture: async () => {
          throw new Error("Could not remove your photo. Please try again.");
        },
      }}
      onSignOut={async () => {}}
    />,
  );
  const menu = await screen.findByLabelText("Account menu for Andrei Example");
  fireEvent.click(menu);
  fireEvent.click(screen.getByRole("button", { name: "Remove profile photo" }));
  await screen.findByRole("alert");
  expect(menu.querySelector("img")?.src).toBe(profile.pictureUrl);
});
