import type { components } from "../generated/api";

export type ConnectionPage = components["schemas"]["ConnectionPageResponse"];
export type RelatedArticle = components["schemas"]["RelatedArticleResponse"];
export type Processing = components["schemas"]["ConnectionProcessingResponse"];
export interface ConnectionsApi {
  list(
    id: string,
    cursor: string | null,
    signal: AbortSignal,
  ): Promise<ConnectionPage>;
  retry(id: string, signal: AbortSignal): Promise<void>;
  remove(id: string, relatedId: string, signal: AbortSignal): Promise<void>;
}
export const pending = (state: Processing["state"]) =>
  ["QUEUED", "PROCESSING", "PAUSED", "RETRY_PENDING"].includes(state);
const uuid = (value: unknown) =>
  typeof value === "string" &&
  /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value);
const date = (value: unknown) =>
  typeof value === "string" && Number.isFinite(Date.parse(value));
const nullableString = (value: unknown) =>
  value === null || typeof value === "string";
function valid(value: unknown): value is ConnectionPage {
  if (!value || typeof value !== "object") return false;
  const page = value as ConnectionPage;
  const state = page.processing;
  return (
    Array.isArray(page.items) &&
    page.items.every((item) => {
      if (
        !item ||
        !uuid(item.id) ||
        !uuid(item.connectionId) ||
        typeof item.title !== "string" ||
        !nullableString(item.entityName) ||
        typeof item.category !== "string" ||
        !date(item.savedAt) ||
        typeof item.semanticUpdating !== "boolean"
      )
        return false;
      try {
        if (!["http:", "https:"].includes(new URL(item.url).protocol))
          return false;
      } catch {
        return false;
      }
      return (
        Array.isArray(item.sharedEntities) &&
        item.sharedEntities.every(
          (e) => e && uuid(e.id) && typeof e.name === "string",
        ) &&
        (item.semanticScore === null ||
          (typeof item.semanticScore === "number" &&
            Number.isFinite(item.semanticScore) &&
            item.semanticScore >= -1 &&
            item.semanticScore <= 1)) &&
        (item.sharedEntities.length > 0 || item.semanticScore !== null)
      );
    }) &&
    nullableString(page.nextCursor) &&
    Boolean(state) &&
    [
      "DISABLED",
      "SKIPPED",
      "QUEUED",
      "PROCESSING",
      "PAUSED",
      "RETRY_PENDING",
      "FAILED",
      "COMPLETE",
    ].includes(state.state) &&
    nullableString(state.reason) &&
    (state.nextAttemptAt === null || date(state.nextAttemptAt)) &&
    Number.isSafeInteger(state.pollIntervalMs) &&
    state.pollIntervalMs > 0 &&
    state.pollIntervalMs <= 2_147_483_647
  );
}
async function call(
  id: string,
  suffix: string,
  method: "GET" | "POST" | "DELETE",
  signal: AbortSignal,
) {
  const message =
    method === "GET"
      ? "Could not load related articles."
      : method === "DELETE"
        ? "Removal was not confirmed. Refresh connections before trying again."
        : "Retry was not confirmed. Refresh connections before trying again.";
  let response: Response;
  try {
    response = await globalThis.fetch(
      `/api/v1/articles/${encodeURIComponent(id)}/connections${suffix}`,
      {
        method,
        credentials: "same-origin",
        headers: { Accept: "application/json" },
        signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
      },
    );
  } catch (cause) {
    if (signal.aborted) throw cause;
    throw new Error(message, { cause });
  }
  if (response.status === 404)
    throw new Error("This article is no longer available.");
  if (response.status === 409)
    throw new Error("Article connections are currently disabled.");
  if (
    response.status !== (method === "GET" ? 200 : method === "POST" ? 202 : 204)
  )
    throw new Error(message);
  return response;
}
export const connectionsApi: ConnectionsApi = {
  async list(id, cursor, signal) {
    const response = await call(
      id,
      cursor ? `?cursor=${encodeURIComponent(cursor)}` : "",
      "GET",
      signal,
    );
    try {
      const page: unknown = await response.json();
      if (!valid(page)) throw new Error("Invalid connections response");
      return page;
    } catch {
      throw new Error("The server returned unreadable related articles.");
    }
  },
  async retry(id, signal) {
    await call(id, "/retry", "POST", signal);
  },
  async remove(id, relatedId, signal) {
    await call(id, `/${encodeURIComponent(relatedId)}`, "DELETE", signal);
  },
};
