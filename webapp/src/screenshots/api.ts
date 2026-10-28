import type { components } from "../generated/api";
export type Screenshot = components["schemas"]["ScreenshotResponse"];
export type ScreenshotReader = (
  articleId: string,
  signal: AbortSignal,
) => Promise<Screenshot>;
export const readScreenshot: ScreenshotReader = async (id, signal) => {
  const response = await globalThis.fetch(
    `/api/v1/articles/${encodeURIComponent(id)}/screenshot`,
    {
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      cache: "no-store",
      signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
    },
  );
  if (response.status !== 200) throw new Error("Screenshot unavailable.");
  const value = (await response.json()) as Screenshot;
  if (
    !value ||
    !["AVAILABLE", "MISSING"].includes(value.status) ||
    !Number.isSafeInteger(value.version) ||
    value.version < 0
  )
    throw new Error("Invalid screenshot response.");
  if (value.status === "AVAILABLE") {
    if (
      typeof value.url !== "string" ||
      !/^https:\/\//.test(value.url) ||
      typeof value.expiresAt !== "string" ||
      Number.isNaN(Date.parse(value.expiresAt))
    )
      throw new Error("Invalid screenshot delivery URL.");
  }
  return value;
};
