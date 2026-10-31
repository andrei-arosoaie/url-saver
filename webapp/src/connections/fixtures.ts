import type { ConnectionPage, RelatedArticle } from "./api";
export const sourceId = "00000000-0000-4000-8000-000000000001";
export const related: RelatedArticle = {
  id: "00000000-0000-4000-8000-000000000002",
  connectionId: "00000000-0000-4000-8000-000000000003",
  title: "Captured title",
  entityName: "Dune",
  category: "BOOKS",
  url: "https://example.test/book",
  savedAt: "2026-10-31T10:00:00Z",
  sharedEntities: [
    { id: "00000000-0000-4000-8000-000000000004", name: "Dune" },
  ],
  semanticScore: 0.9,
  semanticUpdating: false,
};
export const complete: ConnectionPage = {
  items: [related],
  nextCursor: null,
  processing: {
    state: "COMPLETE",
    reason: null,
    nextAttemptAt: null,
    pollIntervalMs: 10000,
  },
};
