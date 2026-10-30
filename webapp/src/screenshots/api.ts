import type { components } from "../generated/api";
export type Screenshot = components["schemas"]["ScreenshotResponse"];
export type ScreenshotReader = (
  articleId: string,
  signal: AbortSignal,
  refresh?: boolean,
) => Promise<Screenshot>;
const urls = new Map<string, Screenshot>();
export const readScreenshot: ScreenshotReader = async (
  id,
  signal,
  refresh = false,
) => {
  if (refresh) urls.delete(id);
  const response = await globalThis.fetch(
    `/api/v1/articles/${encodeURIComponent(id)}/screenshot${refresh ? "?refresh=true" : ""}`,
    {
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      cache: "no-store",
      signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
    },
  );
  if (response.status !== 200) {
    urls.delete(id);
    throw new Error("Screenshot unavailable.");
  }
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
    // Still fetch fresh ownership/version metadata. Reuse only the exact same
    // immutable image, so normal browser image caching survives navigation.
    const cached = urls.get(id);
    if (
      !refresh &&
      cached?.version === value.version &&
      cached.url &&
      cached.expiresAt &&
      Date.parse(cached.expiresAt) > Date.now() + 5000 &&
      cached.contentType === value.contentType &&
      cached.byteSize === value.byteSize &&
      cached.width === value.width &&
      cached.height === value.height
    ) {
      urls.delete(id);
      urls.set(id, cached);
      return cached;
    }
    urls.delete(id);
    urls.set(id, value);
    while (urls.size > 100) urls.delete(urls.keys().next().value!);
  } else {
    urls.delete(id);
  }
  return value;
};
