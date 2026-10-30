import { expect, it, vi } from "vitest";
import { requireWebsiteCaptureAccess } from "./captureAccess";

it("permits website capture when local-file access and all_urls checks are denied", async () => {
  const contains = vi.fn(async ({ origins }: { origins: string[] }) =>
    origins.every(
      (origin) => origin === "http://*/*" || origin === "https://*/*",
    ),
  );
  await expect(requireWebsiteCaptureAccess(contains)).resolves.toBeUndefined();
  expect(contains).toHaveBeenCalledWith({
    origins: ["http://*/*", "https://*/*"],
  });
});

it("identifies a genuinely missing website grant", async () => {
  await expect(
    requireWebsiteCaptureAccess(async () => false),
  ).rejects.toMatchObject({ issue: "PERMISSION" });
});
