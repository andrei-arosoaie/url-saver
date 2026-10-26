import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { entityApi } from "./entities/api";
const emptyEntities = {
  ...entityApi,
  linked: vi.fn().mockResolvedValue({ items: [], nextCursor: null }),
};
import type { Article, LibraryApi, Summary } from "./api";
it("applies an explicit theme to both the app and sandboxed reader", async () => {
  render(<App api={fixture()} entities={emptyEntities} />);
  fireEvent.click(
    await screen.findByRole("button", { name: /A thoughtful article/ }),
  );
  await screen.findByTitle("Saved article content");
  fireEvent.click(screen.getByRole("button", { name: "dark theme" }));
  expect(document.documentElement.dataset.theme).toBe("dark");
  expect(
    screen.getByTitle("Saved article content").getAttribute("srcdoc"),
  ).toContain('data-theme="dark"');
});
const first: Summary = {
  id: "00000000-0000-4000-8000-000000000002",
  url: "https://example.com/article",
  title: "A thoughtful article",
  savedAt: "2026-10-25T00:00:00Z",
  version: 0,
  hasContent: true,
  category: "OTHER",
  entityName: null,
};
const second: Summary = {
  ...first,
  id: "00000000-0000-4000-8000-000000000003",
  title: "Just a link",
  hasContent: false,
  category: "OTHER",
  entityName: null,
};
const detail: Article = {
  ...first,
  content: "<h2>Read this</h2><p>Every word of the article.</p>",
};
function fixture() {
  return {
    list: vi
      .fn<LibraryApi["list"]>()
      .mockResolvedValue({ items: [first, second], nextCursor: null }),
    read: vi.fn<LibraryApi["read"]>().mockResolvedValue(detail),
  };
}
beforeEach(() => history.replaceState(null, "", "/"));
afterEach(cleanup);
describe("responsive article library", () => {
  it("navigates Entities to a linked article and restores Entities on browser Back", async () => {
    const entity = {
      id: "00000000-0000-4000-8000-000000000004",
      type: "BOOK" as const,
      title: "A book entry",
      status: "TO_READ",
      createdAt: first.savedAt,
      version: 0,
      totalCompletionCount: 0,
      lastCompletedAt: null,
    };
    const entities = {
      ...emptyEntities,
      list: vi.fn().mockResolvedValue({
        items: [
          { ...entity, completionCount: 0, latestMatchingCompletionAt: null },
        ],
        nextCursor: null,
      }),
      read: vi.fn().mockResolvedValue(entity),
      years: vi.fn().mockResolvedValue([]),
      history: vi.fn().mockResolvedValue({ items: [], nextCursor: null }),
      sources: vi.fn().mockResolvedValue({ items: [first], nextCursor: null }),
    };
    render(<App api={fixture()} entities={entities} />);
    fireEvent.click(screen.getByRole("button", { name: "Entities" }));
    const source = await screen.findByRole("link", {
      name: `${first.title} ↗`,
    });
    const entityHash = location.hash;
    fireEvent.click(source);
    await screen.findByTitle("Saved article content");
    act(() => {
      history.replaceState(null, "", entityHash);
      window.dispatchEvent(new PopStateEvent("popstate"));
    });
    await screen.findByRole("searchbox", { name: "Search books" });
    expect(
      screen
        .getByRole("button", { name: "Entities" })
        .getAttribute("aria-current"),
    ).toBe("page");
  });
  it("uses the AI catalog record for filtering and explicit metadata edits", async () => {
    const api = {
      ...fixture(),
      categories: vi.fn().mockResolvedValue([
        { key: "AI", displayName: "AI", color: "#0891b2", enabled: true },
        { key: "OTHER", displayName: "Other", color: "#be923b", enabled: true },
      ]),
      updateMetadata: vi
        .fn()
        .mockResolvedValue({ ...detail, category: "AI", version: 1 }),
    };
    render(<App api={api} entities={emptyEntities} />);
    fireEvent.click(await screen.findByRole("button", { name: "AI" }));
    await waitFor(() =>
      expect(api.list.mock.lastCall?.[2]?.category).toBe("AI"),
    );
    fireEvent.click(
      await screen.findByRole("button", { name: /A thoughtful article/ }),
    );
    fireEvent.click(
      await screen.findByRole("button", { name: "Edit details" }),
    );
    fireEvent.change(screen.getByLabelText("Category"), {
      target: { value: "AI" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save details" }));
    await waitFor(() =>
      expect(api.updateMetadata).toHaveBeenCalledWith(
        detail,
        "AI",
        null,
        expect.any(AbortSignal),
      ),
    );
  });
  it("loads category records and restarts pagination when search and category change", async () => {
    const base = fixture();
    const api = {
      ...base,
      categories: vi.fn().mockResolvedValue([
        {
          key: "BOOKS",
          displayName: "Books",
          color: "#168e91",
          enabled: true,
        },
      ]),
    };
    render(<App api={api} entities={emptyEntities} />);
    await screen.findByRole("button", { name: "Books" });
    await screen.findByRole("button", { name: /A thoughtful article/ });
    fireEvent.change(
      screen.getByRole("searchbox", { name: "Search saved entries" }),
      {
        target: { value: "Literal 100%" },
      },
    );

    await waitFor(() =>
      expect(api.list.mock.lastCall?.[2]).toEqual({
        category: undefined,
        query: "Literal 100%",
      }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Books" }));
    await waitFor(() =>
      expect(api.list.mock.lastCall?.[2]).toEqual({
        category: "BOOKS",
        query: "Literal 100%",
      }),
    );
    expect(api.list.mock.lastCall?.[0]).toBeNull();
  });
  it("retries a failed refresh from the first page rather than loading an old cursor", async () => {
    const api = fixture();
    api.list
      .mockResolvedValueOnce({ items: [first], nextCursor: "old-cursor" })
      .mockRejectedValueOnce(new Error("Refresh failed"))
      .mockResolvedValueOnce({ items: [second], nextCursor: null });
    render(<App api={api} entities={emptyEntities} />);
    await screen.findByRole("button", { name: /A thoughtful article/ });
    fireEvent.click(screen.getByRole("button", { name: "Refresh articles" }));
    await screen.findByRole("alert");
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    await screen.findByRole("button", { name: /Just a link/ });
    expect(api.list.mock.calls[2]![0]).toBeNull();
    expect(
      screen.queryByRole("button", { name: /A thoughtful article/ }),
    ).toBeNull();
  });
  it("opens a saved article, preserves its content in a script-disabled reader, and returns to the list", async () => {
    const api = fixture();
    render(<App api={api} entities={emptyEntities} />);
    fireEvent.click(
      await screen.findByRole("button", { name: /A thoughtful article/ }),
    );
    await waitFor(() =>
      expect(screen.getByRole("heading", { name: first.title })).toBeTruthy(),
    );
    const frame = screen.getByTitle("Saved article content");
    expect(frame.getAttribute("srcdoc")).toContain(
      "Every word of the article.",
    );
    expect(frame.getAttribute("sandbox")).not.toContain("allow-scripts");
    expect(frame.getAttribute("srcdoc")).toContain("default-src 'none'");
    const source = screen.getByRole("link", { name: /Open original page/ });
    expect(source.getAttribute("href")).toBe(first.url);
    expect(source.getAttribute("rel")).toContain("noopener");
    fireEvent.click(screen.getByRole("button", { name: "← Timeline" }));
    expect(screen.queryByTitle("Saved article content")).toBeNull();
    expect(location.hash).toBe("");
  });
  it("loads another cursor page without duplicating rows or discarding earlier articles", async () => {
    const api = fixture();
    api.list
      .mockResolvedValueOnce({ items: [first], nextCursor: "opaque-marker" })
      .mockResolvedValueOnce({ items: [first, second], nextCursor: null });
    render(<App api={api} entities={emptyEntities} />);
    fireEvent.click(await screen.findByRole("button", { name: "Load more" }));
    await screen.findByRole("button", { name: /Just a link/ });
    expect(
      screen.getAllByRole("button", { name: /A thoughtful article/ }),
    ).toHaveLength(1);
    expect(api.list.mock.calls[1]![0]).toBe("opaque-marker");
    expect(screen.queryByRole("button", { name: "Load more" })).toBeNull();
  });
  it("shows the empty library without inventing sample articles", async () => {
    const api = fixture();
    api.list.mockResolvedValue({ items: [], nextCursor: null });
    render(<App api={api} entities={emptyEntities} />);
    expect(await screen.findByText(/No saved entries yet/)).toBeTruthy();
    expect(api.read).not.toHaveBeenCalled();
  });
  it("shows link-only explanation and the original URL", async () => {
    const api = fixture();
    api.read.mockResolvedValue({ ...second, content: null });
    render(<App api={api} entities={emptyEntities} />);
    fireEvent.click(await screen.findByRole("button", { name: /Just a link/ }));
    expect(
      await screen.findByText(
        "No content was saved. You can still open the original page.",
      ),
    ).toBeTruthy();
    expect(screen.queryByTitle("Saved article content")).toBeNull();
  });
  it("handles a failed list request and retries explicitly", async () => {
    const api = fixture();
    api.list.mockRejectedValueOnce(new Error("Server unavailable"));
    render(<App api={api} entities={emptyEntities} />);
    await screen.findByRole("alert");
    expect(api.list).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    await screen.findByRole("button", { name: /A thoughtful article/ });
    expect(api.list).toHaveBeenCalledTimes(2);
  });
  it("reads a bookmarked article directly and responds to browser back navigation", async () => {
    history.replaceState(null, "", `/#article/${first.id}`);
    const api = fixture();
    render(<App api={api} entities={emptyEntities} />);
    await screen.findByTitle("Saved article content");
    expect(api.read.mock.calls[0]![0]).toBe(first.id);
    act(() => {
      history.replaceState(null, "", "/");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });
    expect(screen.queryByTitle("Saved article content")).toBeNull();
  });
  it("ignores stale article responses after the user selects another article", async () => {
    const api = fixture();
    let resolveFirst!: (value: Article) => void;
    api.read
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce({ ...second, content: null });
    render(<App api={api} entities={emptyEntities} />);
    fireEvent.click(
      await screen.findByRole("button", { name: /A thoughtful article/ }),
    );
    fireEvent.click(screen.getByRole("button", { name: /Just a link/ }));
    await screen.findByText(
      "No content was saved. You can still open the original page.",
    );
    await act(async () => resolveFirst(detail));
    expect(screen.queryByTitle("Saved article content")).toBeNull();
    expect(screen.getByRole("heading", { name: second.title })).toBeTruthy();
  });
  it("allows a failed detail request to be retried without losing the library", async () => {
    const api = fixture();
    api.read.mockRejectedValueOnce(new Error("Connection lost"));
    render(<App api={api} entities={emptyEntities} />);
    fireEvent.click(
      await screen.findByRole("button", { name: /A thoughtful article/ }),
    );
    await screen.findByRole("alert");
    expect(screen.getByRole("button", { name: /Just a link/ })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    await screen.findByTitle("Saved article content");
    expect(api.read).toHaveBeenCalledTimes(2);
  });
});
