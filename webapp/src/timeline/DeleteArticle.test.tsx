import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { DeleteArticle } from "./DeleteArticle";
import { ArticleConflict, type Article } from "../api";
const article: Article = {
  id: "00000000-0000-4000-8000-000000000002",
  url: "https://example.test/article",
  title: "Saved entry",
  content: "<p>Article</p>",
  savedAt: "2026-10-30T00:00:00Z",
  category: "OTHER",
  entityName: "My chosen name",
  version: 3,
};
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
afterEach(cleanup);
it("requires explicit confirmation and cancellation never deletes", async () => {
  const remove = vi.fn().mockResolvedValue(undefined),
    done = vi.fn();
  render(
    <DeleteArticle
      article={article}
      remove={remove}
      onDeleted={done}
      onReload={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Delete article" }));
  expect(remove).not.toHaveBeenCalled();
  expect(screen.getByText("My chosen name")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Delete article" }));
  fireEvent.click(screen.getByRole("button", { name: "Delete permanently" }));
  await waitFor(() => expect(done).toHaveBeenCalledTimes(1));
  expect(remove).toHaveBeenCalledWith(article, expect.any(AbortSignal));
});
it("reloads after version conflict and never retries or reports deletion", async () => {
  const remove = vi
      .fn()
      .mockRejectedValue(new ArticleConflict("Article changed. Reload it.")),
    done = vi.fn(),
    reload = vi.fn();
  render(
    <DeleteArticle
      article={article}
      remove={remove}
      onDeleted={done}
      onReload={reload}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Delete article" }));
  fireEvent.click(screen.getByRole("button", { name: "Delete permanently" }));
  await screen.findByRole("alert");
  expect(
    screen.queryByRole("button", { name: "Delete permanently" }),
  ).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Reload article" }));
  expect(reload).toHaveBeenCalledTimes(1);
  expect(done).not.toHaveBeenCalled();
  expect(remove).toHaveBeenCalledTimes(1);
});
