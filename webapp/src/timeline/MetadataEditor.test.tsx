import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { MetadataEditor } from "./MetadataEditor";
import type { Article, LibraryApi } from "../api";
import { ArticleConflict } from "../api";
afterEach(cleanup);
it("requires reopening after a version conflict instead of resubmitting the stale version", async () => {
  const update = vi
    .fn()
    .mockRejectedValue(new ArticleConflict("Reopen this article."));
  render(
    <MetadataEditor
      article={{
        id: "a",
        url: "https://example.test",
        title: "A",
        content: null,
        savedAt: "2026-10-25T12:00:00Z",
        version: 0,
        category: "BOOKS",
        entityName: null,
      }}
      categories={[
        { key: "BOOKS", displayName: "Books", color: "#168e91", enabled: true },
      ]}
      api={{ list: vi.fn(), read: vi.fn(), updateMetadata: update }}
      onSaved={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Edit details" }));
  fireEvent.click(screen.getByRole("button", { name: "Save details" }));
  await screen.findByRole("alert");
  expect(
    (screen.getByRole("button", { name: "Save details" }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
  expect(update).toHaveBeenCalledTimes(1);
});
it("clears the identified name explicitly and keeps a failed update open for review", async () => {
  const article: Article = {
    id: "a",
    url: "https://example.test",
    title: "Article",
    content: "<p>Original</p>",
    savedAt: "2026-10-25T12:00:00Z",
    version: 7,
    category: "BOOKS",
    entityName: "Original name",
  };
  const update = vi
    .fn<NonNullable<LibraryApi["updateMetadata"]>>()
    .mockRejectedValueOnce(new Error("Article changed. Reopen it."));
  const saved = vi.fn();
  const api: LibraryApi = {
    list: vi.fn(),
    read: vi.fn(),
    updateMetadata: update,
  };
  render(
    <MetadataEditor
      article={article}
      categories={[
        { key: "BOOKS", displayName: "Books", color: "#168e91", enabled: true },
      ]}
      api={api}
      onSaved={saved}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Edit details" }));
  fireEvent.change(screen.getByLabelText("Identified entity name"), {
    target: { value: "" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save details" }));
  await screen.findByRole("alert");
  expect(update.mock.calls[0]?.slice(0, 3)).toEqual([article, "BOOKS", null]);
  expect(saved).not.toHaveBeenCalled();
  expect(update).toHaveBeenCalledTimes(1);
  update.mockResolvedValueOnce({ ...article, entityName: null, version: 8 });
  fireEvent.click(screen.getByRole("button", { name: "Save details" }));
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith({
      ...article,
      entityName: null,
      version: 8,
    }),
  );
});
