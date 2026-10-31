import type { components } from "../generated/api";

export type Identification = components["schemas"]["IdentificationResponse"];
export interface IdentificationApi {
  status(id: string, signal: AbortSignal): Promise<Identification>;
  request(id: string, signal: AbortSignal): Promise<Identification>;
}
function valid(value: unknown): value is Identification {
  if (!value || typeof value !== "object") return false;
  const data = value as Identification;
  return (
    [
      "NOT_REQUESTED",
      "PENDING",
      "RUNNING",
      "SUCCEEDED",
      "FAILED",
      "SKIPPED",
    ].includes(data.status) &&
    [data.categorySource, data.nameSource].every((v) =>
      ["DEFAULT", "USER", "AI"].includes(v),
    ) &&
    [data.reason, data.provider, data.model].every(
      (v) => v === null || typeof v === "string",
    ) &&
    [data.requestedAt, data.completedAt].every(
      (v) =>
        v === null || (typeof v === "string" && Number.isFinite(Date.parse(v))),
    ) &&
    Array.isArray(data.subjects) &&
    data.subjects.every(
      (s) =>
        s &&
        typeof s.name === "string" &&
        s.name.trim() &&
        typeof s.main === "boolean",
    ) &&
    (data.subjects.length === 0 ||
      data.subjects.filter((s) => s.main).length === 1)
  );
}
async function call(
  id: string,
  method: "GET" | "POST",
  signal: AbortSignal,
): Promise<Identification> {
  let response: Response;
  try {
    response = await globalThis.fetch(
      `/api/v1/articles/${encodeURIComponent(id)}/identification`,
      {
        method,
        credentials: "same-origin",
        headers: { Accept: "application/json" },
        signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
      },
    );
  } catch (error) {
    if (signal.aborted) throw error;
    throw new Error(
      method === "POST"
        ? "Request not confirmed. Refresh status before trying again."
        : "Could not load identification status.",
      { cause: error },
    );
  }
  if (response.status === 404)
    throw new Error("This article is no longer available.");
  if (response.status === 503)
    throw new Error("Identification is currently unavailable.");
  if (response.status !== (method === "POST" ? 202 : 200))
    throw new Error(
      method === "POST"
        ? "Request not confirmed. Refresh status before trying again."
        : "Could not load identification status.",
    );
  try {
    const data: unknown = await response.json();
    if (!valid(data)) throw new Error("Invalid status");
    return data;
  } catch {
    throw new Error(
      method === "POST"
        ? "Request not confirmed. Refresh status before trying again."
        : "The server returned an unreadable identification status.",
    );
  }
}
export const identificationApi: IdentificationApi = {
  status: (id, signal) => call(id, "GET", signal),
  request: (id, signal) => call(id, "POST", signal),
};
