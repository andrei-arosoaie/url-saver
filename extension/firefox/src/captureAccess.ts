import { CaptureFailure } from "./captureFailure";

export async function requireWebsiteCaptureAccess(
  contains: (permissions: { origins: string[] }) => Promise<boolean>,
) {
  // Website screenshots must not depend on the separate local-file grant.
  if (!(await contains({ origins: ["http://*/*", "https://*/*"] })))
    throw new CaptureFailure("PERMISSION");
}
