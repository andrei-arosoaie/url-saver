import type { components } from "../generated/api";
export type Entity = components["schemas"]["EntityResponse"];
export type EntitySummary = components["schemas"]["EntitySummaryResponse"];
export type Completion = components["schemas"]["CompletionResponse"];
export type LinkedEntity = components["schemas"]["LinkedEntityResponse"];
export type SourceArticle = components["schemas"]["SourceArticleResponse"];
export type CompletionYear = components["schemas"]["CompletionYearResponse"];
export type EntityType = Entity["type"];
export type View = "table" | "board" | "year";
export interface EntityRoute {
  type: EntityType;
  view: View;
  id: string | null;
  query: string;
  year: string;
}
export const types = {
  BOOK: {
    label: "Books",
    singular: "book",
    statuses: ["TO_READ", "READING", "READ"],
    done: "READ",
    date: "Read on",
    year: "Read year",
  },
  MOVIE: {
    label: "Movies",
    singular: "movie",
    statuses: ["TO_BE_SEEN", "SEEN"],
    done: "SEEN",
    date: "Seen on",
    year: "Seen year",
  },
  TV_SHOW: {
    label: "TV shows",
    singular: "TV show",
    statuses: ["TO_BE_SEEN", "SEEN"],
    done: "SEEN",
    date: "Seen on",
    year: "Seen year",
  },
  GAME: {
    label: "Games",
    singular: "game",
    statuses: ["TO_PLAY", "PLAYING", "FINISHED"],
    done: "FINISHED",
    date: "Finished on",
    year: "Finished year",
  },
} as const;
export function statusLabel(status: string) {
  return (
    (
      {
        TO_READ: "To read",
        READING: "Reading",
        READ: "Read",
        TO_BE_SEEN: "To be seen",
        SEEN: "Seen",
        TO_PLAY: "To play",
        PLAYING: "Playing",
        FINISHED: "Finished",
      } as Record<string, string>
    )[status] ?? status
  );
}
export function entityRoute(hash: string): EntityRoute {
  const [path, search] = hash.replace(/^#/, "").split("?");
  const [, type, view, id] = path.split("/");
  const params = new URLSearchParams(search);
  return {
    type: type && Object.hasOwn(types, type) ? (type as EntityType) : "BOOK",
    view: view === "board" || view === "year" ? view : "table",
    id: id && /^[0-9a-f-]{36}$/i.test(id) ? id : null,
    query: params.get("q") ?? "",
    year: /^[1-9]\d{0,3}$/.test(params.get("year") ?? "")
      ? params.get("year")!
      : "",
  };
}
export function entityHash(route: EntityRoute) {
  const params = new URLSearchParams();
  if (route.query) params.set("q", route.query);
  if (route.year) params.set("year", route.year);
  return `#entities/${route.type}/${route.view}${route.id ? "/" + route.id : ""}${params.size ? "?" + params : ""}`;
}
export function shortDate(value: string | null) {
  return value
    ? new Intl.DateTimeFormat(undefined, {
        day: "numeric",
        month: "short",
        year: "numeric",
      }).format(new Date(value))
    : "—";
}
export function utcInput(value: string) {
  return value.replace(/Z$/, "");
}
export function fromUtcInput(value: string) {
  const input = value.endsWith("Z") ? value.slice(0, -1) : value;
  const full = input.length === 16 ? input + ":00" : input;
  if (
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?$/.test(full) ||
    Number(full.slice(0, 4)) < 1 ||
    Number.isNaN(Date.parse(full + "Z")) ||
    new Date(full + "Z").toISOString().slice(0, 19) !== full.slice(0, 19)
  )
    throw new Error("Enter a valid UTC timestamp.");
  return full + "Z";
}
