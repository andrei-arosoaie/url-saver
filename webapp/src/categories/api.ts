import type { components } from "../generated/api";
import type { Category } from "../api";

type CategoryResponse = components["schemas"]["CategoryResponse"];
export type ManagedCategory = Category & Required<CategoryResponse>;
export interface CategoryChanges {
  displayName?: string;
  color?: string;
  enabled?: boolean;
}
export interface CategoryApi {
  list(signal: AbortSignal): Promise<ManagedCategory[]>;
  create(
    name: string,
    color: string,
    signal: AbortSignal,
  ): Promise<ManagedCategory>;
  edit(
    category: ManagedCategory,
    changes: CategoryChanges,
    signal: AbortSignal,
  ): Promise<ManagedCategory>;
  remove(category: ManagedCategory, signal: AbortSignal): Promise<void>;
}
export class CategoryApiError extends Error {
  constructor(
    message: string,
    readonly reason: string,
  ) {
    super(message);
  }
}
export function requiresCategoryReload(error: unknown) {
  return (
    error instanceof CategoryApiError &&
    [
      "VERSION_CONFLICT",
      "CATEGORY_IN_USE",
      "RESOURCE_NOT_FOUND",
      "UNCONFIRMED",
    ].includes(error.reason)
  );
}
async function request(
  path: string,
  method: string,
  status: number,
  signal: AbortSignal,
  body?: unknown,
): Promise<unknown> {
  let response: Response;
  try {
    response = await globalThis.fetch(`/api/v1/categories${path}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      credentials: "same-origin",
      signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new CategoryApiError(
      "The request was not confirmed. Reload categories before trying again.",
      "UNCONFIRMED",
    );
  }
  if (response.status !== status) {
    const data = (await response.json().catch(() => null)) as {
      reason?: string;
    } | null;
    const reason = data?.reason ?? "UNEXPECTED_RESPONSE";
    const messages: Record<string, string> = {
      VERSION_CONFLICT:
        "This category changed. Reload categories before trying again.",
      DUPLICATE_CATEGORY_NAME: "You already have a category with this name.",
      CATEGORY_IN_USE:
        "This category contains articles. Remove or move them first, then reload categories.",
      CATEGORY_REQUIRED: "Other must stay enabled for new saves.",
      DEFAULT_CATEGORY_PROTECTED: "Predefined categories cannot be removed.",
      RESOURCE_NOT_FOUND:
        "This category is no longer available. Reload categories.",
      INVALID_REQUEST:
        "Check the category name and six-digit color, then try again.",
    };
    throw new CategoryApiError(
      messages[reason] ?? "Could not update categories. Reload and try again.",
      reason,
    );
  }
  if (status === 204) return undefined;
  try {
    return await response.json();
  } catch {
    throw new CategoryApiError(
      "The response was unreadable. Reload categories before trying again.",
      "UNCONFIRMED",
    );
  }
}
function category(value: unknown): ManagedCategory {
  const c = value as ManagedCategory | null;
  if (
    !c ||
    typeof c.key !== "string" ||
    typeof c.displayName !== "string" ||
    !/^#[0-9a-f]{6}$/i.test(c.color) ||
    typeof c.enabled !== "boolean" ||
    typeof c.isDefault !== "boolean" ||
    !Number.isSafeInteger(c.version) ||
    c.version < 0 ||
    !Number.isSafeInteger(c.articleCount) ||
    c.articleCount < 0 ||
    typeof c.createdAt !== "string" ||
    Number.isNaN(Date.parse(c.createdAt))
  )
    throw new CategoryApiError(
      "The server returned an unexpected category. Reload categories.",
      "UNCONFIRMED",
    );
  return c;
}
export const categoryApi: CategoryApi = {
  async list(signal) {
    const data = (await request("", "GET", 200, signal)) as {
      items?: unknown[];
    } | null;
    if (!data || !Array.isArray(data.items))
      throw new Error("Could not load categories.");
    return data.items.map(category);
  },
  async create(name, color, signal) {
    return category(
      await request("", "POST", 201, signal, { displayName: name, color }),
    );
  },
  async edit(current, changes, signal) {
    return category(
      await request(
        `/${encodeURIComponent(current.key)}`,
        "PATCH",
        200,
        signal,
        { version: current.version, ...changes },
      ),
    );
  },
  async remove(current, signal) {
    await request(
      `/${encodeURIComponent(current.key)}?version=${current.version}`,
      "DELETE",
      204,
      signal,
    );
  },
};
