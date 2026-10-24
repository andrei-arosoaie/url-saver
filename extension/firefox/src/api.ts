import type { components } from "./generated/api";
import type { Capture } from "./capture";
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
    private request: typeof fetch = fetch,
  ) {}
  private async call(
    path: string,
    method: string,
    body?: unknown,
  ): Promise<{ status: number; data: unknown }> {
    const token = await this.token();
    const response = await this.request(new URL(path, this.origin), {
      method,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(60_000),
      redirect: "error",
      credentials: "omit",
    });
    const data: unknown = await response.json().catch(() => null);
    if (!response.ok) {
      const error = data as Partial<
        components["schemas"]["ApiErrorResponse"]
      > | null;
      throw new ApiFailure(
        response.status,
        error?.reason,
        error?.existingArticleId,
        response.status === 401
          ? "Saving is unavailable. Please try again after the server is updated."
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
      !isArticle(result.article)
    )
      throw new Error("Unexpected server response. Save was not confirmed.");
    return result;
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
