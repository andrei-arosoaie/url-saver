import type { components } from "../generated/api";
import {
  types,
  type Entity,
  type EntitySummary,
  type EntityType,
  type Completion,
  type LinkedEntity,
  type SourceArticle,
  type CompletionYear,
} from "./model";
export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}
export interface EntityFilters {
  type: EntityType;
  query?: string;
  completionYear?: number;
}
export class EntityApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public reason?: string,
    public existingEntityId?: string,
    public uncertain = false,
  ) {
    super(message);
  }
}
export function requiresReload(error: unknown) {
  return (
    error instanceof EntityApiError &&
    (error.uncertain || error.reason === "VERSION_CONFLICT")
  );
}
export interface EntityApi {
  list(
    filters: EntityFilters,
    cursor: string | null,
    signal: AbortSignal,
  ): Promise<Page<EntitySummary>>;
  read(id: string, signal: AbortSignal): Promise<Entity>;
  years(
    type: EntityType,
    query: string,
    signal: AbortSignal,
  ): Promise<CompletionYear[]>;
  create(
    data: components["schemas"]["CreateEntityRequest"],
    signal: AbortSignal,
  ): Promise<Entity>;
  update(
    entity: Entity,
    data: Pick<components["schemas"]["EditEntityRequest"], "title" | "status">,
    signal: AbortSignal,
  ): Promise<Entity>;
  history(
    id: string,
    cursor: string | null,
    signal: AbortSignal,
  ): Promise<Page<Completion>>;
  complete(
    entity: Entity,
    time: string | undefined,
    signal: AbortSignal,
  ): Promise<components["schemas"]["AddCompletionResponse"]>;
  correct(
    event: Completion,
    time: string,
    signal: AbortSignal,
  ): Promise<Completion>;
  deleteCompletion(event: Completion, signal: AbortSignal): Promise<void>;
  sources(
    id: string,
    cursor: string | null,
    signal: AbortSignal,
  ): Promise<Page<SourceArticle>>;
  linked(
    id: string,
    cursor: string | null,
    signal: AbortSignal,
  ): Promise<Page<LinkedEntity>>;
  addFromArticle(
    id: string,
    type: EntityType,
    title: string,
    signal: AbortSignal,
  ): Promise<components["schemas"]["AddEntityResponse"]>;
  unlink(
    articleId: string,
    entityId: string,
    signal: AbortSignal,
  ): Promise<void>;
}
const object = (v: unknown): v is Record<string, unknown> =>
  v !== null && typeof v === "object";
const text = (v: unknown): v is string => typeof v === "string";
const count = (v: unknown) =>
  typeof v === "number" && Number.isSafeInteger(v) && v >= 0;
const time = (v: unknown) => text(v) && !Number.isNaN(Date.parse(v));
const nullableTime = (v: unknown) => v === null || time(v);
export function isEntity(v: unknown): v is Entity {
  return (
    object(v) &&
    text(v.id) &&
    text(v.title) &&
    text(v.type) &&
    Object.hasOwn(types, v.type) &&
    text(v.status) &&
    (types[v.type as EntityType].statuses as readonly string[]).includes(
      v.status,
    ) &&
    time(v.createdAt) &&
    count(v.version) &&
    count(v.totalCompletionCount) &&
    nullableTime(v.lastCompletedAt)
  );
}
function isSummary(v: unknown): v is EntitySummary {
  return (
    isEntity(v) &&
    "completionCount" in v &&
    count(v.completionCount) &&
    "latestMatchingCompletionAt" in v &&
    nullableTime(v.latestMatchingCompletionAt)
  );
}
function isCompletion(v: unknown): v is Completion {
  return (
    object(v) &&
    text(v.id) &&
    text(v.entityId) &&
    time(v.completedAt) &&
    time(v.createdAt) &&
    count(v.version)
  );
}
function isLinked(v: unknown): v is LinkedEntity {
  return isEntity(v) && "linkedAt" in v && time(v.linkedAt);
}
function isSource(v: unknown): v is SourceArticle {
  return (
    object(v) &&
    text(v.id) &&
    text(v.title) &&
    text(v.url) &&
    /^https?:\/\//i.test(v.url) &&
    time(v.savedAt) &&
    count(v.version) &&
    typeof v.hasContent === "boolean" &&
    text(v.category) &&
    (v.entityName === null || text(v.entityName))
  );
}
function page<T>(guard: (v: unknown) => v is T) {
  return (v: unknown): v is Page<T> =>
    object(v) &&
    Array.isArray(v.items) &&
    v.items.every(guard) &&
    (v.nextCursor === null || text(v.nextCursor));
}
async function send<T>(
  method: string,
  path: string,
  data: unknown,
  signal: AbortSignal,
  statuses: number[],
  valid: (v: unknown) => v is T,
): Promise<T> {
  const mutation = method !== "GET";
  let response: Response;
  try {
    response = await globalThis.fetch("/api/v1" + path, {
      method,
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(data === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: data === undefined ? undefined : JSON.stringify(data),
      signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
    });
  } catch (cause) {
    if (signal.aborted) throw cause;
    throw new EntityApiError(
      mutation
        ? "Could not confirm the change. Reload before trying again."
        : "Could not load entities. Please try again.",
      0,
      undefined,
      undefined,
      mutation,
    );
  }
  if (!statuses.includes(response.status)) {
    let error: Record<string, unknown> = {};
    try {
      const value: unknown = await response.json();
      if (object(value)) error = value;
    } catch {
      /* A response need not contain JSON. */
    }
    const reason = text(error.reason) ? error.reason : undefined;
    const message =
      reason === "VERSION_CONFLICT"
        ? "This record changed. Reload before editing again."
        : reason === "DUPLICATE_ENTITY"
          ? "An entity with this exact title and type already exists."
          : response.status === 404
            ? "This record is no longer available."
            : response.status === 400 && text(error.message)
              ? error.message
              : mutation
                ? "Could not confirm the change. Reload before trying again."
                : "Could not load entities. Please try again.";
    throw new EntityApiError(
      message,
      response.status,
      reason,
      text(error.existingEntityId) ? error.existingEntityId : undefined,
      mutation && (response.status >= 500 || response.ok),
    );
  }
  if (response.status === 204) return undefined as T;
  try {
    const value: unknown = await response.json();
    if (valid(value)) return value;
  } catch {
    /* Malformed successful JSON is also unconfirmed. */
  }
  throw new EntityApiError(
    mutation
      ? "The change returned an unreadable response. Reload before trying again."
      : "The server returned unexpected entity data.",
    response.status,
    undefined,
    undefined,
    mutation,
  );
}
const id = (value: string) => encodeURIComponent(value);
const cursor = (value: string | null) =>
  value ? "?cursor=" + encodeURIComponent(value) : "";
export const entityApi: EntityApi = {
  list(filters, bookmark, signal) {
    const params = new URLSearchParams({ type: filters.type });
    if (filters.query) params.set("query", filters.query);
    if (filters.completionYear)
      params.set("completionYear", String(filters.completionYear));
    if (bookmark) params.set("cursor", bookmark);
    return send(
      "GET",
      "/entities?" + params,
      undefined,
      signal,
      [200],
      page(isSummary),
    );
  },
  read(entity, signal) {
    return send(
      "GET",
      "/entities/" + id(entity),
      undefined,
      signal,
      [200],
      isEntity,
    );
  },
  async years(type, query, signal) {
    const params = new URLSearchParams({ type });
    if (query) params.set("query", query);
    const result = await send(
      "GET",
      "/entities/years?" + params,
      undefined,
      signal,
      [200],
      (v): v is { years: CompletionYear[] } =>
        object(v) &&
        Array.isArray(v.years) &&
        v.years.every(
          (y) =>
            object(y) &&
            typeof y.year === "number" &&
            Number.isInteger(y.year) &&
            count(y.entityCount) &&
            count(y.completionCount),
        ),
    );
    return result.years;
  },
  create(data, signal) {
    return send("POST", "/entities", data, signal, [201], isEntity);
  },
  update(entity, data, signal) {
    return send(
      "PATCH",
      "/entities/" + id(entity.id),
      { version: entity.version, ...data },
      signal,
      [200],
      isEntity,
    );
  },
  history(entity, bookmark, signal) {
    return send(
      "GET",
      "/entities/" + id(entity) + "/completions" + cursor(bookmark),
      undefined,
      signal,
      [200],
      page(isCompletion),
    );
  },
  complete(entity, completedAt, signal) {
    return send(
      "POST",
      "/entities/" + id(entity.id) + "/completions",
      { version: entity.version, ...(completedAt ? { completedAt } : {}) },
      signal,
      [201],
      (v): v is components["schemas"]["AddCompletionResponse"] =>
        object(v) && isEntity(v.entity) && isCompletion(v.completion),
    );
  },
  correct(event, completedAt, signal) {
    return send(
      "PATCH",
      `/entities/${id(event.entityId)}/completions/${id(event.id)}`,
      { version: event.version, completedAt },
      signal,
      [200],
      isCompletion,
    );
  },
  deleteCompletion(event, signal) {
    return send(
      "DELETE",
      `/entities/${id(event.entityId)}/completions/${id(event.id)}?version=${event.version}`,
      undefined,
      signal,
      [204],
      (v): v is void => v === undefined,
    );
  },
  sources(entity, bookmark, signal) {
    return send(
      "GET",
      "/entities/" + id(entity) + "/articles" + cursor(bookmark),
      undefined,
      signal,
      [200],
      page(isSource),
    );
  },
  linked(article, bookmark, signal) {
    return send(
      "GET",
      "/articles/" + id(article) + "/entities" + cursor(bookmark),
      undefined,
      signal,
      [200],
      page(isLinked),
    );
  },
  addFromArticle(article, type, title, signal) {
    return send(
      "POST",
      "/articles/" + id(article) + "/entities",
      { type, title },
      signal,
      [200, 201],
      (v): v is components["schemas"]["AddEntityResponse"] =>
        object(v) &&
        isEntity(v.entity) &&
        ["CREATED", "LINKED", "UNCHANGED"].includes(String(v.outcome)) &&
        object(v.link) &&
        v.link.articleId === article &&
        v.link.entityId === v.entity.id &&
        time(v.link.linkedAt),
    );
  },
  unlink(article, entity, signal) {
    return send(
      "DELETE",
      `/articles/${id(article)}/entities/${id(entity)}`,
      undefined,
      signal,
      [204],
      (v): v is void => v === undefined,
    );
  },
};
