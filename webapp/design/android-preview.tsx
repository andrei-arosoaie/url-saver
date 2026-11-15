// Review-only design: uses the production web components and CSS unchanged.
// All data and changes are local to this page; no server request is permitted.
import React from "react";
import { createRoot } from "react-dom/client";
import { App } from "../src/App";
import { ProfileMenu } from "../src/profile/ProfileMenu";
import "../src/global.css";
import type { LibraryApi, Article } from "../src/api";
import type { EntityApi } from "../src/entities/api";
import type { CategoryApi, ManagedCategory } from "../src/categories/api";
import type { Entity } from "../src/entities/model";

const id = (n: number) =>
  `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const time = "2026-11-08T09:12:00.123456Z";
let categories: ManagedCategory[] = [
  ["TECHNOLOGY", "Technology", "#2876dc", 1],
  ["BOOKS", "Books", "#9864bd", 1],
  ["MOVIES", "Movies", "#d08839", 1],
  ["TV_SHOWS", "TV shows", "#168e91", 1],
  ["GAMES", "Games", "#cf576b", 1],
  ["AI", "AI", "#8654b0", 0],
  ["OTHER", "Other", "#66738d", 1],
].map(([key, displayName, color, articleCount]) => ({
  key: String(key),
  displayName: String(displayName),
  color: String(color),
  articleCount: Number(articleCount),
  enabled: true,
  isDefault: true,
  createdAt: time,
  version: 0,
}));
let articles: (Article & { hasContent: boolean })[] = [
  [1, "PostgreSQL indexes, explained", "TECHNOLOGY", null],
  [2, "Dune: a world worth returning to", "BOOKS", "Dune"],
  [3, "The art of Arrival", "MOVIES", "Arrival"],
  [4, "Severance and the divided self", "TV_SHOWS", "Severance"],
  [5, "Exploring the world of Hades", "GAMES", "Hades"],
  [6, "A quieter way to collect ideas", "OTHER", null],
].map(([n, title, category, entityName]) => ({
  id: id(n as number),
  title,
  category,
  entityName,
  url: `https://example.com/library/${n}`,
  savedAt: time,
  version: 0,
  hasContent: true,
  content: `<h2>${title}</h2><p>This sample article is used to review the existing Know reader design on Android phones and tablets.</p><p>Saved pages, linked entities and related articles stay together in your library. Your metadata and completion history follow the same rules as the web application.</p><h3>A place for what matters</h3><p>Keep the original source close, review the details, and return to your timeline when you are ready.</p>`,
})) as (Article & { hasContent: boolean })[];
let entities: Entity[] = [
  [21, "BOOK", "Dune", "READ", 2],
  [22, "BOOK", "The Left Hand of Darkness", "READING", 0],
  [23, "BOOK", "The Dispossessed", "TO_READ", 0],
  [24, "MOVIE", "Arrival", "SEEN", 1],
  [25, "TV_SHOW", "Severance", "TO_BE_SEEN", 0],
  [26, "GAME", "Hades", "PLAYING", 0],
].map(([n, type, title, status, count]) => ({
  id: id(n as number),
  type,
  title,
  status,
  createdAt: time,
  version: 0,
  totalCompletionCount: count,
  lastCompletedAt: count ? time : null,
})) as Entity[];
const page = <T,>(items: T[]) => ({ items, nextCursor: null });
const api: LibraryApi = {
  identification: {
    status: async () => ({
      status: "SUCCEEDED",
      categorySource: "AI",
      nameSource: "USER",
      reason: null,
      provider: "Sample provider",
      model: "Sample model",
      requestedAt: time,
      completedAt: time,
      subjects: [],
    }),
    request: async () => ({
      status: "SUCCEEDED",
      categorySource: "AI",
      nameSource: "USER",
      reason: null,
      provider: "Sample provider",
      model: "Sample model",
      requestedAt: time,
      completedAt: time,
      subjects: [],
    }),
  },
  list: async (_cursor, _signal, filters) =>
    page(
      articles.filter(
        (a) =>
          (!filters?.category || a.category === filters.category) &&
          (!filters?.query ||
            a.title.toLowerCase().includes(filters.query.toLowerCase())),
      ),
    ),
  read: async (key) => structuredClone(articles.find((a) => a.id === key)!),
  categories: async () => structuredClone(categories),
  deleteArticle: async (article) => {
    articles = articles.filter((a) => a.id !== article.id);
  },
  updateMetadata: async (article, category, entityName) => {
    const a = articles.find((a) => a.id === article.id)!;
    Object.assign(a, {
      category: category ?? a.category,
      entityName: entityName === undefined ? a.entityName : entityName,
      version: a.version + 1,
    });
    return structuredClone(a);
  },
  connections: {
    list: async () => ({
      ...page([]),
      processing: {
        state: "COMPLETE",
        reason: null,
        nextAttemptAt: null,
        pollIntervalMs: 10000,
      },
    }),
    retry: async () => {
      throw Error("Sample design only");
    },
    remove: async () => {},
  } as LibraryApi["connections"],
};
const entityApi: EntityApi = {
  list: async (filters) =>
    page(
      entities
        .filter(
          (e) =>
            e.type === filters.type &&
            (!filters.query ||
              e.title.toLowerCase().includes(filters.query.toLowerCase())),
        )
        .map((e) => ({
          ...e,
          completionCount: e.totalCompletionCount,
          latestMatchingCompletionAt: e.lastCompletedAt,
        })),
    ),
  read: async (key) => structuredClone(entities.find((e) => e.id === key)!),
  years: async () => [{ year: 2026, entityCount: 1, completionCount: 2 }],
  history: async (key) =>
    page(
      entities.find((e) => e.id === key)?.totalCompletionCount
        ? [
            {
              id: id(81),
              entityId: key,
              completedAt: time,
              createdAt: time,
              version: 0,
            },
          ]
        : [],
    ),
  sources: async () => page(articles.slice(1, 2)),
  linked: async (key) =>
    page(
      key === id(2)
        ? entities
            .filter((e) => e.id === id(21))
            .map((e) => ({ ...e, linkedAt: time }))
        : [],
    ),
  create: async (data) => {
    const e = {
      ...data,
      id: crypto.randomUUID(),
      createdAt: time,
      version: 0,
      totalCompletionCount: 0,
      lastCompletedAt: null,
    } as Entity;
    entities.push(e);
    return e;
  },
  update: async (entity, data) => {
    const e = entities.find((e) => e.id === entity.id)!;
    Object.assign(e, data, { version: e.version + 1 });
    return structuredClone(e);
  },
  remove: async (entity) => {
    entities = entities.filter((e) => e.id !== entity.id);
  },
  complete: async (entity) => ({
    entity: {
      ...entity,
      totalCompletionCount: entity.totalCompletionCount + 1,
      lastCompletedAt: time,
    },
    completion: {
      id: id(82),
      entityId: entity.id,
      completedAt: time,
      createdAt: time,
      version: 0,
    },
  }),
  correct: async (event, completedAt) => ({
    ...event,
    completedAt,
    version: event.version + 1,
  }),
  deleteCompletion: async () => {},
  unlink: async () => {},
  addFromArticle: async () => {
    throw Error("Sample design only");
  },
};
const categoryApi: CategoryApi = {
  list: async () => structuredClone(categories),
  create: async (displayName, color) => {
    const c = {
      key: crypto.randomUUID(),
      displayName,
      color,
      enabled: true,
      isDefault: false,
      createdAt: time,
      articleCount: 0,
      version: 0,
    } as ManagedCategory;
    categories.push(c);
    return c;
  },
  edit: async (category, changes) => {
    const c = categories.find((c) => c.key === category.key)!;
    Object.assign(c, changes, { version: c.version + 1 });
    return structuredClone(c);
  },
  remove: async (category) => {
    categories = categories.filter((c) => c.key !== category.key);
  },
};
globalThis.fetch = async (input) => {
  const url = String(input);
  if (url.includes("/screenshot"))
    return Response.json({ status: "NONE", version: 0 });
  if (url.endsWith("/tvmaze/settings"))
    return Response.json({
      enabled: true,
      minimumCharacters: 2,
      debounceMs: 300,
      pageSize: 10,
    });
  if (url.endsWith("/episodes"))
    return Response.json({
      items: Array.from({ length: 9 }, (_, i) => ({
        id: i + 1,
        seasonId: 1,
        season: 1,
        number: i + 1,
        title: [
          "Good News About Hell",
          "Half Loop",
          "In Perpetuity",
          "The You You Are",
          "The Grim Barbarity of Optics and Design",
          "Hide and Seek",
          "Defiant Jazz",
          "What’s for Dinner?",
          "The We We Are",
        ][i],
        airAt: time,
        airDate: "2026-11-08",
        runtime: 50,
        imageUrl: null,
        summary: null,
        watched: i < 3,
        canMarkWatched: true,
      })),
    });
  if (url.endsWith("/tv-show/seasons"))
    return Response.json({
      items: [
        {
          id: 1,
          number: 1,
          name: null,
          regularEpisodeCount: 9,
          airedCount: 9,
          watchedCount: 3,
        },
      ],
    });
  if (url.endsWith("/tv-show"))
    return Response.json({
      entityId: id(25),
      tvmazeId: 1,
      version: 0,
      entityVersion: 0,
      canonicalTitle: "Severance",
      alias: null,
      status: "TO_BE_SEEN",
      show: {
        name: "Severance",
        posterUrl: null,
        summary:
          "A team discovers the cost of separating work and personal life.",
        genres: ["Drama", "Science-Fiction"],
        providerStatus: "Running",
        averageRuntime: 50,
        tvmazeRating: 8.5,
        imdbUrl: null,
      },
      lastWatchedEpisodeId: null,
      watchedCount: 3,
      airedCount: 9,
      newEpisodesAvailable: true,
      canMarkSeen: true,
      metadata: { state: "READY", fetchedAt: time, nextAttemptAt: null },
    });
  return new Response(
    JSON.stringify({
      message: "Not available in this isolated design preview",
    }),
    { status: 404, headers: { "Content-Type": "application/json" } },
  );
};
createRoot(document.getElementById("root")!).render(
  <App
    api={api}
    entities={entityApi}
    categoryManagement={categoryApi}
    accountControl={
      <ProfileMenu
        api={
          {
            get: async () => ({
              displayName: "Alex Morgan",
              pictureUrl: null,
              pictureRemoved: false,
            }),
            removePicture: async () => ({
              displayName: "Alex Morgan",
              pictureUrl: null,
              pictureRemoved: false,
            }),
            restorePicture: async () => ({
              displayName: "Alex Morgan",
              pictureUrl: null,
              pictureRemoved: false,
            }),
          } as never
        }
        onSignOut={async () => {}}
      />
    }
  />,
);

// file:// frames have isolated origins. Report readiness without cross-frame DOM access.
let reportPending = false;
const report = () => {
  if (reportPending) return;
  reportPending = true;
  setTimeout(() => {
    reportPending = false;
    if (window.parent !== window)
      window.parent.postMessage(
        {
          type: "know-android-mock-ready",
          width: innerWidth,
          route: location.hash,
          text: document.body.innerText,
        },
        "*",
      );
  });
};
new MutationObserver(report).observe(document.getElementById("root")!, {
  childList: true,
  subtree: true,
  characterData: true,
});
window.addEventListener("hashchange", report);
