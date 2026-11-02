import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { PosterPreview } from "./PosterPreview";
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
afterEach(cleanup);
it("opens an enlarged poster and restores focus after closing", async () => {
  render(
    <PosterPreview src="https://example.test/poster.jpg" title="Severance" />,
  );
  const button = screen.getByRole("button", {
    name: "Enlarge poster for Severance",
  });
  fireEvent.click(button);
  expect(
    screen.getByRole("dialog", { name: "Poster for Severance" }),
  ).toBeTruthy();
  expect(
    screen.getByAltText("Severance poster enlarged").getAttribute("src"),
  ).toBe("https://example.test/poster.jpg");
  fireEvent.click(screen.getByRole("button", { name: "Close poster" }));
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  expect(document.activeElement).toBe(button);
  fireEvent.click(button);
  fireEvent(screen.getByRole("dialog"), new Event("cancel", { bubbles: true }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

it("uses the original image for the enlarged preview when available", () => {
  render(
    <PosterPreview
      src="https://example.test/medium.jpg"
      originalSrc="https://example.test/original.jpg"
      title="Show"
    />,
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Enlarge poster for Show" }),
  );
  expect(screen.getByAltText("Show poster enlarged").getAttribute("src")).toBe(
    "https://example.test/original.jpg",
  );
  expect(screen.getByAltText("Show poster").getAttribute("src")).toBe(
    "https://example.test/medium.jpg",
  );
  fireEvent.error(screen.getByAltText("Show poster enlarged"));
  expect(screen.getByAltText("Show poster enlarged").getAttribute("src")).toBe(
    "https://example.test/medium.jpg",
  );
});
