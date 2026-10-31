import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { App } from "./App";
import { entityApi } from "./entities/api";
import type { Article, LibraryApi } from "./api";
import type { Identification } from "./identification/api";
afterEach(cleanup);
it("updates the reader and timeline on completion, then preserves a manual name without freezing the category", async () => {
  history.replaceState(null, "", "/");
  let article: Article = {
    id: "00000000-0000-4000-8000-000000000002",
    url: "https://example.test",
    title: "Captured title",
    content: "<p>A book article</p>",
    savedAt: "2026-10-31T00:00:00Z",
    version: 0,
    category: "OTHER",
    entityName: null,
  };
  let status: Identification = {
    status: "PENDING",
    reason: null,
    provider: null,
    model: null,
    subjects: [],
    categorySource: "DEFAULT",
    nameSource: "DEFAULT",
    requestedAt: "2026-10-31T00:00:00Z",
    completedAt: null,
  };
  const read = vi
    .fn<LibraryApi["read"]>()
    .mockImplementation(async () => article);
  const update = vi
    .fn<NonNullable<LibraryApi["updateMetadata"]>>()
    .mockImplementation(async (current, category, name) => {
      expect(current.version).toBe(1);
      expect(category).toBeUndefined();
      article = { ...article, entityName: name ?? null, version: 2 };
      status = { ...status, nameSource: "USER", subjects: [] };
      return article;
    });
  const identification = {
    status: vi.fn().mockImplementation(async () => status),
    request: vi.fn(),
  };
  render(
    <App
      api={{
        list: vi.fn().mockImplementation(async () => ({
          items: [{ ...article, hasContent: true }],
          nextCursor: null,
        })),
        read,
        updateMetadata: update,
        identification,
        categories: vi.fn().mockResolvedValue([
          {
            key: "OTHER",
            displayName: "Other",
            color: "#be923b",
            enabled: true,
          },
          {
            key: "BOOKS",
            displayName: "Books",
            color: "#168e91",
            enabled: true,
          },
        ]),
      }}
      entities={{
        ...entityApi,
        linked: vi.fn().mockResolvedValue({ items: [], nextCursor: null }),
      }}
    />,
  );
  fireEvent.click(
    await screen.findByRole("button", { name: /Captured title/ }),
  );
  await screen.findByText("Waiting to identify…");
  article = { ...article, category: "BOOKS", entityName: "1984", version: 1 };
  status = {
    ...status,
    status: "SUCCEEDED",
    provider: "openrouter",
    model: "openrouter/free",
    categorySource: "AI",
    nameSource: "AI",
    subjects: [
      { name: "1984", main: true },
      { name: "Animal Farm", main: false },
    ],
    completedAt: "2026-10-31T00:00:05Z",
  };
  act(() => document.dispatchEvent(new Event("visibilitychange")));
  await screen.findByText("Identification complete");
  await within(
    screen.getByRole("complementary", { name: "Saved articles" }),
  ).findByRole("button", { name: /1984/ });
  const panel = screen.getByRole("region", { name: "AI identification" });
  expect(within(panel).getByText("Animal Farm")).toBeTruthy();
  expect(within(panel).getAllByText("Main subject")).toHaveLength(1);
  await waitFor(() => expect(read).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByRole("button", { name: "Edit details" }));
  fireEvent.change(screen.getByLabelText("Identified entity name"), {
    target: { value: "My chosen name" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save details" }));
  await within(panel).findByText("Your choice");
  expect(within(panel).getByText("AI")).toBeTruthy();
  await within(
    screen.getByRole("complementary", { name: "Saved articles" }),
  ).findByRole("button", { name: /My chosen name/ });
  expect(read).toHaveBeenCalledTimes(2);
  expect(update).toHaveBeenCalledTimes(1);
  expect(identification.request).not.toHaveBeenCalled();
});
