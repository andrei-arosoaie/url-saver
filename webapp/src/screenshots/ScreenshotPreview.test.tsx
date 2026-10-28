import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ScreenshotPreview } from "./ScreenshotPreview";
import type { Screenshot } from "./api";

afterEach(cleanup);
beforeEach(() => {
  // jsdom has no native dialog implementation.
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
});
const available = (url: string): Screenshot => ({
  status: "AVAILABLE",
  version: 1,
  url,
  expiresAt: new Date(Date.now() + 1800000).toISOString(),
  contentType: "image/jpeg",
  byteSize: 123,
  width: 1920,
  height: 1080,
});

it("keeps the enlarged view open while renewing an expired URL", async () => {
  const expired = {
    ...available("https://images.test/expired"),
    expiresAt: "2020-01-01T00:00:00Z",
  };
  const read = vi
    .fn()
    .mockResolvedValueOnce(expired)
    .mockResolvedValueOnce(available("https://images.test/fresh"));
  render(<ScreenshotPreview articleId="one" read={read} />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Enlarge saved screenshot" }),
  );
  await waitFor(() =>
    expect(
      screen.getByAltText("Saved page viewport enlarged").getAttribute("src"),
    ).toBe("https://images.test/fresh"),
  );
  expect(screen.getByRole("dialog").hasAttribute("open")).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Close" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

it("refreshes a failed signed URL once and then displays the missing-image placeholder", async () => {
  const read = vi
    .fn()
    .mockResolvedValueOnce(available("https://images.test/first"))
    .mockResolvedValueOnce(available("https://images.test/renewed"));
  render(<ScreenshotPreview articleId="one" read={read} />);
  fireEvent.error(await screen.findByAltText("Saved page viewport"));
  await waitFor(() =>
    expect(screen.getByAltText("Saved page viewport").getAttribute("src")).toBe(
      "https://images.test/renewed",
    ),
  );
  fireEvent.error(screen.getByAltText("Saved page viewport"));
  await screen.findByText("Screenshot unavailable");
  expect(read).toHaveBeenCalledTimes(2);
  expect(screen.getByText("Article saved successfully.")).toBeTruthy();
});

it("ignores a late response for an article the reader has left", async () => {
  let finish!: (value: Screenshot) => void;
  const read = vi
    .fn()
    .mockImplementationOnce(
      () =>
        new Promise<Screenshot>((resolve) => {
          finish = resolve;
        }),
    )
    .mockResolvedValueOnce(available("https://images.test/current"));
  const view = render(<ScreenshotPreview articleId="old" read={read} />);
  view.rerender(<ScreenshotPreview articleId="current" read={read} />);
  await screen.findByAltText("Saved page viewport");
  finish(available("https://images.test/old"));
  await waitFor(() =>
    expect(screen.getByAltText("Saved page viewport").getAttribute("src")).toBe(
      "https://images.test/current",
    ),
  );
});
