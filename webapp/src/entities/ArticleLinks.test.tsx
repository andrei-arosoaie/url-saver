import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import {
  useArticleLinks,
  ArticleLinkButton,
  ArticleLinkedEntities,
} from "./ArticleLinks";
import { entityApi, EntityApiError } from "./api";
import type { Article } from "../api";
const article: Article = {
  id: "00000000-0000-4000-8000-000000000001",
  url: "https://example.test",
  title: "A page about Dune",
  content: "<p>Saved text</p>",
  savedAt: "2026-10-25T18:00:00Z",
  category: "BOOKS",
  entityName: "Dune",
  version: 0,
};
const entity = {
  id: "00000000-0000-4000-8000-000000000002",
  type: "BOOK" as const,
  title: "Dune",
  status: "READ",
  version: 4,
  createdAt: article.savedAt,
  lastCompletedAt: article.savedAt,
  totalCompletionCount: 2,
};
afterEach(cleanup);
function Harness({
  api,
  entry = article,
}: {
  api: typeof entityApi;
  entry?: Article;
}) {
  const state = useArticleLinks(entry, api);
  return (
    <>
      <ArticleLinkButton state={state} />
      <ArticleLinkedEntities state={state} onOpen={vi.fn()} />
    </>
  );
}
it("preserves existing associations when the identified name changes", async () => {
  const api = {
    ...entityApi,
    linked: vi.fn().mockResolvedValue({
      items: [{ ...entity, linkedAt: article.savedAt }],
      nextCursor: null,
    }),
    addFromArticle: vi.fn(),
    unlink: vi.fn().mockResolvedValue(undefined),
  };
  const view = render(<Harness api={api} />);
  await screen.findByRole("button", { name: "Remove Dune from this page" });
  expect(screen.queryByRole("button", { name: "Unlink Dune" })).toBeNull();
  view.rerender(
    <Harness
      api={api}
      entry={{ ...article, entityName: "Foundation", version: 1 }}
    />,
  );
  expect(screen.getByRole("button", { name: "Unlink Dune" })).toBeTruthy();
  expect(api.addFromArticle).not.toHaveBeenCalled();
  expect(api.unlink).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Unlink Dune" }));
  await waitFor(() =>
    expect(api.unlink).toHaveBeenCalledWith(
      article.id,
      entity.id,
      expect.any(AbortSignal),
    ),
  );
});
it("adds the identified entity then unlinks only the stable article/entity pair", async () => {
  let linked = false;
  const link = {
    articleId: article.id,
    entityId: entity.id,
    linkedAt: article.savedAt,
  };
  const api = {
    ...entityApi,
    linked: vi.fn().mockImplementation(async () => ({
      items: linked ? [{ ...entity, linkedAt: link.linkedAt }] : [],
      nextCursor: null,
    })),
    addFromArticle: vi.fn().mockImplementation(async () => {
      linked = true;
      return { outcome: "LINKED" as const, entity, link };
    }),
    unlink: vi.fn().mockImplementation(async () => {
      linked = false;
    }),
  };
  render(<Harness api={api} />);
  await waitFor(() =>
    expect(
      (
        screen.getByRole("button", {
          name: "Add Dune to Books",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(false),
  );
  fireEvent.click(screen.getByRole("button", { name: "Add Dune to Books" }));
  await waitFor(() =>
    expect(
      (
        screen.getByRole("button", {
          name: "Remove Dune from this page",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(false),
  );
  expect(api.addFromArticle).toHaveBeenCalledWith(
    article.id,
    "BOOK",
    "Dune",
    expect.any(AbortSignal),
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Remove Dune from this page" }),
  );
  await waitFor(() =>
    expect(api.unlink).toHaveBeenCalledWith(
      article.id,
      entity.id,
      expect.any(AbortSignal),
    ),
  );
  await waitFor(() =>
    expect(
      (
        screen.getByRole("button", {
          name: "Add Dune to Books",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(false),
  );
});
it("requires reloading links after an unconfirmed write without automatically retrying", async () => {
  const api = {
    ...entityApi,
    linked: vi.fn().mockResolvedValue({ items: [], nextCursor: null }),
    addFromArticle: vi
      .fn()
      .mockRejectedValue(
        new EntityApiError(
          "Reload before retrying",
          0,
          undefined,
          undefined,
          true,
        ),
      ),
  };
  render(<Harness api={api} />);
  const add = await screen.findByRole("button", { name: "Add Dune to Books" });
  await waitFor(() => expect((add as HTMLButtonElement).disabled).toBe(false));
  fireEvent.click(add);
  await screen.findByRole("alert");
  expect(api.addFromArticle).toHaveBeenCalledTimes(1);
  expect((add as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Reload links" }));
  await waitFor(() => expect((add as HTMLButtonElement).disabled).toBe(false));
  expect(api.addFromArticle).toHaveBeenCalledTimes(1);
});
