import { afterEach, expect, it, vi } from "vitest";
import { CaptureFailure } from "./captureFailure";
import { ApiFailure } from "./api";
import { reportScreenshotFailure } from "./screenshotDiagnostics";

afterEach(() => vi.restoreAllMocks());
it("logs the capture stage and original browser cause without URLs or credentials", () => {
  const log = vi.spyOn(console, "error").mockImplementation(() => {});
  reportScreenshotFailure(
    "capture",
    new CaptureFailure(
      "CAPTURE_FAILED",
      new Error(
        "Missing permission https://private.test/?token=secret Authorization: Bearer abc123",
      ),
    ),
  );
  expect(log).toHaveBeenCalledWith(
    "Know: screenshot capture failed.",
    expect.objectContaining({
      reason: "CAPTURE_FAILED",
      cause: expect.stringContaining("Missing permission"),
    }),
  );
  expect(JSON.stringify(log.mock.calls)).not.toMatch(
    /private\.test|secret|abc123/,
  );
});
it("logs a rejected screenshot upload with its HTTP status and stable reason", () => {
  const log = vi.spyOn(console, "error").mockImplementation(() => {});
  reportScreenshotFailure(
    "upload",
    new ApiFailure(413, "SCREENSHOT_TOO_LARGE"),
  );
  expect(log).toHaveBeenCalledWith(
    "Know: screenshot upload failed.",
    expect.objectContaining({ status: 413, reason: "SCREENSHOT_TOO_LARGE" }),
  );
});
