import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CategoriesPage } from "./CategoriesPage";
import {
  CategoryApiError,
  type CategoryApi,
  type ManagedCategory,
} from "./api";
const custom: ManagedCategory = {
  key: "custom",
  displayName: "Research",
  color: "#112233",
  enabled: true,
  isDefault: false,
  createdAt: "2026-10-30T00:00:00Z",
  version: 0,
  articleCount: 0,
};
function fixture(rows = [custom]) {
  return {
    list: vi.fn<CategoryApi["list"]>().mockResolvedValue(rows),
    create: vi
      .fn<CategoryApi["create"]>()
      .mockResolvedValue({ ...custom, key: "new", displayName: "New name" }),
    edit: vi
      .fn<CategoryApi["edit"]>()
      .mockImplementation(async (c, changes) => ({
        ...c,
        ...changes,
        version: c.version + 1,
      })),
    remove: vi.fn<CategoryApi["remove"]>().mockResolvedValue(undefined),
  };
}
afterEach(cleanup);
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
it("creates edits disables reenables and confirms removal while publishing catalog changes", async () => {
  const api = fixture();
  const changed = vi.fn();
  render(<CategoriesPage api={api} onChanged={changed} onArticles={vi.fn()} />);
  await screen.findByText("Research");
  fireEvent.click(screen.getByRole("button", { name: "Edit Research" }));
  let modal = screen.getByRole("dialog");
  fireEvent.change(within(modal).getByLabelText("Name"), {
    target: { value: "Reading" },
  });
  fireEvent.click(within(modal).getByRole("button", { name: "Save category" }));
  await screen.findByRole("button", { name: "Disable Reading" });
  expect(api.edit).toHaveBeenCalledWith(
    custom,
    { displayName: "Reading", color: "#112233" },
    expect.any(AbortSignal),
  );
  fireEvent.click(screen.getByRole("button", { name: "Disable Reading" }));
  fireEvent.click(
    await screen.findByRole("button", { name: "Enable Reading" }),
  );
  await screen.findByRole("button", { name: "Disable Reading" });
  fireEvent.click(screen.getByRole("button", { name: "Remove Reading" }));
  expect(api.remove).not.toHaveBeenCalled();
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }),
  );
  expect(api.remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Remove Reading" }));
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "Remove category",
    }),
  );
  await screen.findByText("Category removed.");
  expect(api.remove.mock.calls[0]![0].version).toBe(3);
  fireEvent.click(screen.getByRole("button", { name: "New category" }));
  modal = screen.getByRole("dialog");
  fireEvent.change(within(modal).getByLabelText("Name"), {
    target: { value: "New name" },
  });
  fireEvent.click(within(modal).getByRole("button", { name: "Save category" }));
  await screen.findByText("New name");
  expect(changed.mock.lastCall![0]).toEqual([
    { ...custom, key: "new", displayName: "New name" },
  ]);
});
it("shows counts and blocks referenced categories while keeping all names editable", async () => {
  const api = fixture([
    { ...custom, articleCount: 3 },
    {
      ...custom,
      key: "BOOKS",
      displayName: "Books",
      isDefault: true,
      articleCount: 2,
    },
    { ...custom, key: "OTHER", displayName: "Other", isDefault: true },
  ]);
  const open = vi.fn();
  render(<CategoriesPage api={api} onChanged={vi.fn()} onArticles={open} />);
  await screen.findByText("Research");
  expect(
    (
      screen.getByRole("button", {
        name: "Disable Research",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  expect(
    (
      screen.getByRole("button", {
        name: "Remove Research",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  expect(screen.queryByRole("button", { name: "Remove Books" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Disable Other" })).toBeNull();
  fireEvent.click(
    screen.getByRole("button", { name: "View 3 articles in Research" }),
  );
  expect(open).toHaveBeenCalledWith("custom");
  fireEvent.click(screen.getByRole("button", { name: "Edit Books" }));
  expect((screen.getByLabelText("Name") as HTMLInputElement).value).toBe(
    "Books",
  );
});
it("requires reload after a stale edit without automatically resubmitting", async () => {
  const api = fixture();
  api.edit.mockRejectedValue(
    new CategoryApiError(
      "Category changed. Reload categories.",
      "VERSION_CONFLICT",
    ),
  );
  render(<CategoriesPage api={api} onChanged={vi.fn()} onArticles={vi.fn()} />);
  fireEvent.click(await screen.findByRole("button", { name: "Edit Research" }));
  fireEvent.click(screen.getByRole("button", { name: "Save category" }));
  await screen.findByRole("alert");
  expect(screen.queryByRole("button", { name: "Save category" })).toBeNull();
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "Reload categories",
    }),
  );
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
  expect(api.edit).toHaveBeenCalledTimes(1);
});
it("refreshes after a server detects new article references", async () => {
  const api = fixture();
  api.edit.mockRejectedValue(
    new CategoryApiError("Remove or move articles first.", "CATEGORY_IN_USE"),
  );
  render(<CategoriesPage api={api} onChanged={vi.fn()} onArticles={vi.fn()} />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Disable Research" }),
  );
  await screen.findByRole("alert");
  expect(
    (
      screen.getByRole("button", {
        name: "Disable Research",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  api.list.mockResolvedValue([{ ...custom, articleCount: 1 }]);
  fireEvent.click(screen.getByRole("button", { name: "Reload categories" }));
  await screen.findByRole("button", { name: "View 1 articles in Research" });
  expect(api.edit).toHaveBeenCalledTimes(1);
});
