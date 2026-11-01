import { EntityApiError } from "./api";
import type { Entity } from "./model";

export interface Candidate {
  tvmazeId: number;
  name: string;
  premiereYear: number | null;
  posterUrl: string | null;
  providerStatus: string | null;
}
export interface Search {
  items: Candidate[];
  state: string;
  matchStatus: string;
  minimumCharacters: number;
  debounceMs: number;
  pageSize: number;
}
export interface Season {
  id: number;
  number: number;
  name: string | null;
  regularEpisodeCount: number;
  airedCount: number;
  watchedCount: number;
}
export interface Episode {
  id: number;
  seasonId: number;
  season: number;
  number: number | null;
  title: string;
  airAt: string | null;
  airDate: string | null;
  runtime: number | null;
  imageUrl: string | null;
  summary: string | null;
  watched: boolean;
  canMarkWatched: boolean;
}
export interface TvDetails {
  entityId: string;
  tvmazeId: number;
  version: number;
  entityVersion: number;
  canonicalTitle: string;
  alias: string | null;
  status: string;
  show: {
    name: string;
    posterUrl: string | null;
    summary: string | null;
    genres: string[];
    providerStatus: string | null;
    averageRuntime: number | null;
    tvmazeRating: number | null;
    imdbUrl: string | null;
    nextEpisode?: Episode | null;
  } | null;
  lastWatchedEpisodeId: number | null;
  watchedCount: number;
  airedCount: number;
  newEpisodesAvailable: boolean;
  canMarkSeen: boolean;
  metadata: {
    state: string;
    fetchedAt: string | null;
    nextAttemptAt: string | null;
  };
}
export async function tvRequest<T>(
  path: string,
  signal: AbortSignal,
  method = "GET",
  data?: unknown,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch("/api/v1" + path, {
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
      method === "GET"
        ? "Show information is unavailable. Try again."
        : "Could not confirm the change. Reload before trying again.",
      0,
      undefined,
      undefined,
      method !== "GET",
    );
  }
  const value: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const error = value as {
      reason?: string;
      message?: string;
      existingEntityId?: string;
    } | null;
    throw new EntityApiError(
      error?.reason === "VERSION_CONFLICT"
        ? "This show changed. Reload before editing again."
        : (error?.message ?? "Show information is unavailable."),
      response.status,
      error?.reason,
      error?.existingEntityId,
      response.status >= 500 && method !== "GET",
    );
  }
  if (!validResponse(path, value))
    throw new EntityApiError(
      "Unexpected show response. Reload before continuing.",
      response.status,
      undefined,
      undefined,
      method !== "GET",
    );
  return value as T;
}

function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
const count = (value: unknown) =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
const nullableText = (value: unknown) =>
  value === null || typeof value === "string";
function candidate(value: unknown) {
  return (
    object(value) &&
    count(value.tvmazeId) &&
    typeof value.name === "string" &&
    nullableText(value.posterUrl) &&
    (value.premiereYear === null || count(value.premiereYear))
  );
}
function validResponse(path: string, value: unknown): boolean {
  if (!object(value)) return false;
  if (path === "/tvmaze/settings")
    return (
      count(value.minimumCharacters) &&
      Number(value.minimumCharacters) >= 2 &&
      count(value.debounceMs) &&
      Number(value.debounceMs) > 0 &&
      count(value.pageSize) &&
      Number(value.pageSize) > 0
    );
  if (path.startsWith("/tvmaze/shows?"))
    return (
      Array.isArray(value.items) &&
      value.items.every(candidate) &&
      typeof value.state === "string" &&
      typeof value.matchStatus === "string" &&
      count(value.pageSize) &&
      Number(value.pageSize) > 0
    );
  if (path === "/tvmaze/entities")
    return (
      typeof value.id === "string" &&
      value.type === "TV_SHOW" &&
      typeof value.title === "string" &&
      typeof value.status === "string" &&
      count(value.version)
    );
  if (path.endsWith("/seasons"))
    return (
      Array.isArray(value.items) &&
      value.items.every(
        (s) =>
          object(s) &&
          count(s.id) &&
          count(s.number) &&
          count(s.airedCount) &&
          count(s.watchedCount),
      )
    );
  if (path.endsWith("/episodes"))
    return (
      Array.isArray(value.items) &&
      value.items.every(
        (e) =>
          object(e) &&
          count(e.id) &&
          count(e.season) &&
          typeof e.title === "string" &&
          nullableText(e.airAt) &&
          nullableText(e.airDate) &&
          typeof e.watched === "boolean" &&
          typeof e.canMarkWatched === "boolean",
      )
    );
  return (
    typeof value.entityId === "string" &&
    count(value.version) &&
    count(value.entityVersion) &&
    typeof value.canonicalTitle === "string" &&
    nullableText(value.alias) &&
    typeof value.status === "string" &&
    count(value.watchedCount) &&
    count(value.airedCount) &&
    typeof value.canMarkSeen === "boolean" &&
    typeof value.newEpisodesAvailable === "boolean" &&
    object(value.metadata) &&
    typeof value.metadata.state === "string" &&
    (value.show === null ||
      (object(value.show) &&
        typeof value.show.name === "string" &&
        Array.isArray(value.show.genres) &&
        value.show.genres.every((g) => typeof g === "string")))
  );
}
export const tvPath = (id: string) =>
  `/entities/${encodeURIComponent(id)}/tv-show`;
export const createTv = (
  candidate: Candidate,
  status: string,
  signal: AbortSignal,
) =>
  tvRequest<Entity>("/tvmaze/entities", signal, "POST", {
    tvmazeId: candidate.tvmazeId,
    status,
  });
