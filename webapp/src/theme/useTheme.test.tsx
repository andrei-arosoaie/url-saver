import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useTheme } from "./useTheme";
import { ThemePicker } from "./ThemePicker";
let dark = false;
let changed = () => {};
function Fixture() {
  const theme = useTheme();
  return (
    <>
      <ThemePicker
        preference={theme.preference}
        onChange={theme.setPreference}
      />
      <output aria-label="Resolved theme">{theme.resolved}</output>
    </>
  );
}
beforeEach(() => {
  localStorage.clear();
  dark = false;
  vi.stubGlobal("matchMedia", () => ({
    get matches() {
      return dark;
    },
    addEventListener: (_: string, listener: () => void) => {
      changed = listener;
    },
    removeEventListener: () => {},
  }));
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  delete document.documentElement.dataset.theme;
});
describe("Phase 2 theme selection", () => {
  it("follows OS changes in system mode", () => {
    render(<Fixture />);
    act(() => {
      dark = true;
      changed();
    });
    expect(screen.getByLabelText("Resolved theme").textContent).toBe("dark");
    expect(
      screen
        .getByRole("button", { name: "system theme" })
        .getAttribute("aria-pressed"),
    ).toBe("true");
  });
  it("keeps an explicit light selection despite an OS change", () => {
    render(<Fixture />);
    fireEvent.click(screen.getByRole("button", { name: "light theme" }));
    act(() => {
      dark = true;
      changed();
    });
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(localStorage.getItem("know-theme")).toBe("light");
  });
  it("restores a saved dark selection", () => {
    localStorage.setItem("know-theme", "dark");
    render(<Fixture />);
    expect(screen.getByLabelText("Resolved theme").textContent).toBe("dark");
    expect(
      screen
        .getByRole("button", { name: "dark theme" })
        .getAttribute("aria-pressed"),
    ).toBe("true");
  });
});
