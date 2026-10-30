export type CaptureIssue =
  | "PERMISSION"
  | "UNAVAILABLE"
  | "PAGE_CHANGED"
  | "PREPARATION"
  | "CAPTURE_FAILED"
  | "TOO_LARGE"
  | "LOCAL_STORAGE";
const messages: Record<CaptureIssue, string> = {
  PERMISSION:
    "Enable Know's access to all websites in Firefox add-on permissions, then reload this page and save again.",
  UNAVAILABLE:
    "Firefox's screenshot API is unavailable. Restart Firefox after enabling Know's access to all websites.",
  PAGE_CHANGED:
    "The active page changed during capture. Return to the article and save again.",
  PREPARATION:
    "Screenshot preparation failed. Reload this page after updating Know, then save again.",
  CAPTURE_FAILED:
    "Firefox could not capture this page. Check Know's website permissions, reload the page and save again.",
  TOO_LARGE: "The native screenshot exceeds the 20 MiB upload limit.",
  LOCAL_STORAGE:
    "Firefox could not retain the screenshot for upload. Check available extension storage.",
};
export const captureIssueMessage = (issue: CaptureIssue) => messages[issue];
export class CaptureFailure extends Error {
  constructor(
    public issue: CaptureIssue,
    cause?: unknown,
  ) {
    super(messages[issue], { cause });
  }
}
