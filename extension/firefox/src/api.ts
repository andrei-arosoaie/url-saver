import type { components } from "./generated/api";
import type { Capture } from "./capture";
export class ServerNonResponse extends Error {
  constructor() {
    super("Server did not respond. Your save is not confirmed.");
  }
}
export class ApiFailure extends Error {
  constructor(
    public status: number,
    public reason?: string,
    public existingArticleId?: string,
    message = "Save not confirmed. Click Save to retry.",
  ) {
    super(message);
  }
}
type Article = components["schemas"]["ArticleResponse"];
export class ArticleApi {
  constructor(
    private origin: string,
    private token: () => Promise<string | undefined>,
    private request: typeof fetch = globalThis.fetch.bind(globalThis),
  ) {}
  private async call(
    path: string,
    method: string,
    body?: unknown,
  ): Promise<{ status: number; data: unknown }> {
    const token = await this.token();
    let response: Response;
    try {
      response = await this.request(new URL(path, this.origin), {
        method,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(body && !(body instanceof FormData)
            ? { "Content-Type": "application/json" }
            : {}),
        },
        body:
          body instanceof FormData
            ? body
            : body
              ? JSON.stringify(body)
              : undefined,
        signal: AbortSignal.timeout(60_000),
        redirect: "error",
        credentials: "omit",
      });
    } catch (error) {
      if (isTransportFailure(error)) throw new ServerNonResponse();
      throw error;
    }
    const data: unknown = await response.json().catch((error: unknown) => {
      if (response.ok && isTransportFailure(error))
        throw new ServerNonResponse();
      return null;
    });
    if (!response.ok) {
      const error = data as Partial<
        components["schemas"]["ApiErrorResponse"]
      > | null;
      throw new ApiFailure(
        response.status,
        error?.reason,
        error?.existingArticleId,
        response.status === 401
          ? "Sign in to Know, then click Save again."
          : response.status === 413
            ? "Article exceeds the server content limit. Nothing was saved."
            : error?.reason === "VERSION_CONFLICT"
              ? "Article changed. Cancel, then save again to review the latest version."
              : "Save not confirmed. Click Save to retry.",
      );
    }
    return { status: response.status, data };
  }
  async save(capture: Capture) {
    const { status, data } = await this.call(
      "/api/v1/articles",
      "POST",
      capture,
    );
    const result = data as components["schemas"]["SaveArticleResponse"];
    if (
      ![200, 201].includes(status) ||
      !result ||
      !["CREATED", "ENRICHED", "UNCHANGED"].includes(result.outcome) ||
      !isArticle(result.article) ||
      result.article.url !== capture.url
    )
      throw new Error("Unexpected server response. Save was not confirmed.");
    return result;
  }
  async screenshotVersion(id: string): Promise<number> {
    const { status, data } = await this.call(
      `/api/v1/articles/${encodeURIComponent(id)}/screenshot`,
      "GET",
    );
    const value = data as { version?: number } | null;
    if (
      status !== 200 ||
      !Number.isSafeInteger(value?.version) ||
      value!.version! < 0
    )
      throw new Error("Invalid screenshot response.");
    return value!.version!;
  }
  async uploadScreenshot(
    id: string,
    captureId: string,
    version: number,
    dataUrl?: string,
  ): Promise<boolean> {
    const body = new FormData();
    body.append(
      "metadata",
      new Blob([JSON.stringify({ captureId, expectedVersion: version })], {
        type: "application/json",
      }),
    );
    if (dataUrl) {
      const prefix = "data:image/png;base64,";
      if (!dataUrl.startsWith(prefix))
        throw new Error("Invalid screenshot capture.");
      const binary = atob(dataUrl.slice(prefix.length));
      body.append(
        "file",
        new Blob([Uint8Array.from(binary, (c) => c.charCodeAt(0))], {
          type: "image/png",
        }),
        "viewport.png",
      );
    }
    const { status, data } = await this.call(
      `/api/v1/articles/${encodeURIComponent(id)}/screenshot`,
      "PUT",
      body,
    );
    const value = data as { status?: string } | null;
    if (
      status !== 200 ||
      !value ||
      !["AVAILABLE", "MISSING"].includes(value.status ?? "")
    )
      throw new Error("Invalid screenshot upload response.");
    return value.status === "AVAILABLE";
  }
  async existing(id: string): Promise<Article> {
    const { status, data } = await this.call(
      `/api/v1/articles/${encodeURIComponent(id)}`,
      "GET",
    );
    if (status !== 200 || !isArticle(data))
      throw new Error("Unable to read the existing article.");
    return data;
  }
  async update(
    id: string,
    version: number,
    capture: Capture,
  ): Promise<Article> {
    const { status, data } = await this.call(
      `/api/v1/articles/${encodeURIComponent(id)}`,
      "PATCH",
      {
        version,
        title: capture.title,
        ...(capture.content ? { content: capture.content } : {}),
      },
    );
    if (status !== 200 || !isArticle(data))
      throw new Error("Unexpected update response.");
    return data;
  }
}
function isTransportFailure(error: unknown): boolean {
  return (
    !!error &&
    typeof error === "object" &&
    "name" in error &&
    ["TypeError", "TimeoutError", "AbortError", "NetworkError"].includes(
      String(error.name),
    )
  );
}
function isArticle(value: unknown): value is Article {
  if (!value || typeof value !== "object") return false;
  const a = value as Article;
  return (
    typeof a.id === "string" &&
    typeof a.url === "string" &&
    typeof a.title === "string" &&
    typeof a.savedAt === "string" &&
    Number.isSafeInteger(a.version) &&
    a.version >= 0 &&
    (a.content === null || typeof a.content === "string")
  );
}
