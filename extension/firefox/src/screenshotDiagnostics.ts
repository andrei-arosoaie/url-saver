import { ApiFailure } from "./api";
import { CaptureFailure } from "./captureFailure";

// Keep useful browser error messages, never captured content, signed URLs or keys.
const safeMessage = (value: string) =>
  value
    .replace(/(?:https?|file|moz-extension):\/\/\S+/gi, "[URL]")
    .replace(/\bBearer\s+\S+/gi, "Bearer [redacted]")
    .replace(
      /(authorization|token|password|private[-_ ]?key)\s*[:=]\s*\S+/gi,
      "$1=[redacted]",
    )
    .slice(0, 500);
export function reportScreenshotFailure(
  stage: "capture" | "upload" | "storage",
  error: unknown,
) {
  console.error(`Know: screenshot ${stage} failed.`, {
    reason:
      error instanceof CaptureFailure
        ? error.issue
        : error instanceof ApiFailure
          ? error.reason
          : undefined,
    status: error instanceof ApiFailure ? error.status : undefined,
    error: error instanceof Error ? error.name : "UnknownError",
    message:
      error instanceof Error
        ? safeMessage(error.message)
        : "Unknown screenshot failure",
    cause:
      error instanceof Error && error.cause instanceof Error
        ? safeMessage(error.cause.message)
        : undefined,
  });
}
