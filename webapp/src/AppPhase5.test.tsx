import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { App } from "./App";
import { entityApi } from "./entities/api";
import type { Article, LibraryApi } from "./api";
import type { CategoryApi, ManagedCategory } from "./categories/api";
const category: ManagedCategory = {
  key: "BOOKS",
  displayName: "Books",
  color: "#112233",
  enabled: true,
  isDefault: true,
  version: 0,
  articleCount: 1,
  createdAt: "2026-10-30T00:00:00Z",
};
const article: Article = {
  id: "00000000-0000-4000-8000-000000000002",
  url: "https://example.test/a",
  title: "Stored title",
  content: null,
  category: "BOOKS",
  entityName: null,
  savedAt: category.createdAt,
  version: 2,
};
function fixture() {
  const library = {
    list: vi.fn<LibraryApi["list"]>().mockResolvedValue({
      items: [{ ...article, hasContent: false }],
      nextCursor: null,
    }),
    read: vi.fn<LibraryApi["read"]>().mockResolvedValue(article),
    categories: vi.fn().mockResolvedValue([category]),
    deleteArticle: vi.fn().mockResolvedValue(undefined),
  };
  const management = {
    list: vi.fn<CategoryApi["list"]>().mockResolvedValue([category]),
    create: vi.fn<CategoryApi["create"]>(),
    edit: vi
      .fn<CategoryApi["edit"]>()
      .mockImplementation(async (c, changes) => ({
        ...c,
        ...changes,
        version: c.version + 1,
      })),
    remove: vi.fn<CategoryApi["remove"]>(),
  };
  return { library, management };
}
const entities = {
  ...entityApi,
  linked: vi.fn().mockResolvedValue({ items: [], nextCursor: null }),
};
beforeEach(() => {
  history.replaceState(null, "", "/");
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
afterEach(cleanup);
it("navigates categories counts to timeline and preserves renamed labels through browser Back", async () => {
  const { library, management } = fixture();
  render(
    <App api={library} entities={entities} categoryManagement={management} />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Categories" }));
  expect(location.hash).toBe("#categories");
  await screen.findByRole("heading", { name: "Categories" });
  expect(screen.queryByRole("searchbox")).toBeNull();
  fireEvent.click(await screen.findByRole("button", { name: "Edit Books" }));
  fireEvent.change(screen.getByLabelText("Name"), {
    target: { value: "Reading" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save category" }));
  fireEvent.click(
    await screen.findByRole("button", { name: "View 1 articles in Reading" }),
  );
  await waitFor(() =>
    expect(library.list.mock.lastCall?.[2]?.category).toBe("BOOKS"),
  );
  expect(screen.getByRole("button", { name: "Reading" })).toBeTruthy();
  act(() => {
    history.replaceState(null, "", "#categories");
    window.dispatchEvent(new PopStateEvent("popstate"));
  });
  await screen.findByRole("heading", { name: "Categories" });
  expect(
    screen
      .getByRole("button", { name: "Categories" })
      .getAttribute("aria-current"),
  ).toBe("page");
});
it("removes a confirmed article from the reader and refreshes timeline and counts", async () => {
  const { library, management } = fixture();
  render(
    <App api={library} entities={entities} categoryManagement={management} />,
  );
  fireEvent.click(await screen.findByRole("button", { name: /Stored title/ }));
  fireEvent.click(
    await screen.findByRole("button", { name: "Delete article" }),
  );
  expect(library.deleteArticle).not.toHaveBeenCalled();
  library.list.mockResolvedValue({ items: [], nextCursor: null });
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "Delete permanently",
    }),
  );
  await screen.findByText(/No saved entries yet/);
  expect(location.hash).toBe("");
  expect(screen.queryByRole("heading", { name: "Stored title" })).toBeNull();
  expect(library.deleteArticle).toHaveBeenCalledWith(
    article,
    expect.any(AbortSignal),
  );
  await waitFor(() => expect(library.categories).toHaveBeenCalledTimes(2));
});
