import type { components } from "./generated/api";
export type Article = components["schemas"]["ArticleResponse"];
export type Summary = components["schemas"]["ArticleSummaryResponse"];
export type Page = components["schemas"]["ArticlePageResponse"];
export class ArticleConflict extends Error {}
export interface Category {
  key: string;
  displayName: string;
  color: string;
  enabled: boolean;
}
export interface Filters {
  category?: string;
  query?: string;
}
export interface LibraryApi {
  list(
    cursor: string | null,
    signal: AbortSignal,
    filters?: Filters,
  ): Promise<Page>;
  read(id: string, signal: AbortSignal): Promise<Article>;
  categories?(signal: AbortSignal): Promise<Category[]>;
  updateMetadata?(
    article: Article,
    category: string,
    entityName: string | null,
    signal: AbortSignal,
  ): Promise<Article>;
}
async function request(path: string, signal: AbortSignal): Promise<unknown> {
  const response = await globalThis.fetch(path, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
    headers: { Accept: "application/json" },
    credentials: "same-origin",
  });
  if (!response.ok)
    throw new Error(
      response.status === 404
        ? "This article is no longer available."
        : response.status === 401
          ? "The library is not available on this server yet."
          : "Could not load your articles. Please try again.",
    );
  if (response.status !== 200)
    throw new Error("The server returned an unexpected response.");
  try {
    return await response.json();
  } catch {
    throw new Error(
      "The server returned an unreadable response. Please try again.",
    );
  }
}
function summary(value: unknown): value is Summary {
  if (!value || typeof value !== "object") return false;
  const a = value as Summary;
  return (
    typeof a.id === "string" &&
    typeof a.title === "string" &&
    typeof a.url === "string" &&
    /^https?:\/\//i.test(a.url) &&
    typeof a.savedAt === "string" &&
    !Number.isNaN(Date.parse(a.savedAt)) &&
    Number.isSafeInteger(a.version) &&
    a.version >= 0
  );
}
export const libraryApi: LibraryApi = {
  async updateMetadata(article, category, entityName, signal) {
    const response = await globalThis.fetch(
      `/api/v1/articles/${encodeURIComponent(article.id)}`,
      {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        credentials: "same-origin",
        signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
        body: JSON.stringify({
          version: article.version,
          category,
          entityName,
        }),
      },
    );
    if (response.status === 409)
      throw new ArticleConflict(
        "This article changed. Reopen it before editing again.",
      );
    if (response.status !== 200)
      throw new Error("Could not update the article. Please try again.");
    const data = (await response.json()) as Article;
    if (
      !summary(data) ||
      (data.content !== null && typeof data.content !== "string")
    )
      throw new Error("The server returned an unexpected article.");
    return data;
  },
  async list(cursor, signal, filters) {
    const params = new URLSearchParams({ limit: "20" });
    if (cursor) params.set("cursor", cursor);
    if (filters?.category) params.set("category", filters.category);
    if (filters?.query) params.set("query", filters.query);
    const data = (await request(`/api/v1/articles?${params}`, signal)) as Page;
    if (
      !data ||
      !Array.isArray(data.items) ||
      !data.items.every(
        (a) => summary(a) && typeof a.hasContent === "boolean",
      ) ||
      (data.nextCursor !== null && typeof data.nextCursor !== "string")
    )
      throw new Error("The server returned an unexpected article list.");
    return data;
  },
  async categories(signal) {
    const data = (await request("/api/v1/categories", signal)) as {
      items: Category[];
    };
    if (
      !data ||
      !Array.isArray(data.items) ||
      !data.items.every(
        (c) =>
          typeof c.key === "string" &&
          typeof c.displayName === "string" &&
          /^#[0-9a-f]{6}$/i.test(c.color) &&
          typeof c.enabled === "boolean",
      )
    )
      throw new Error("Could not load categories.");
    return data.items;
  },
  async read(id, signal) {
    const data = (await request(
      `/api/v1/articles/${encodeURIComponent(id)}`,
      signal,
    )) as Article;
    if (
      !summary(data) ||
      (data.content !== null && typeof data.content !== "string")
    )
      throw new Error("The server returned an unexpected article.");
    return data;
  },
};
