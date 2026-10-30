import { afterEach, describe, expect, it, vi } from "vitest";
import { libraryApi } from "./api";
const item = {
  id: "00000000-0000-4000-8000-000000000002",
  url: "https://example.com/article",
  title: "Article",
  savedAt: "2026-10-25T00:00:00Z",
  version: 0,
  hasContent: true,
  category: "OTHER",
  entityName: null,
};
afterEach(() => vi.unstubAllGlobals());
it("permanently deletes using the displayed article version and requires exactly 204", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(new Response(null, { status: 202 }));
  vi.stubGlobal("fetch", fetch);
  const article = { ...item, content: null, version: 3 };
  await libraryApi.deleteArticle!(article, new AbortController().signal);
  expect(fetch.mock.calls[0]![0]).toBe(`/api/v1/articles/${item.id}?version=3`);
  expect(fetch.mock.calls[0]![1].method).toBe("DELETE");
  await expect(
    libraryApi.deleteArticle!(article, new AbortController().signal),
  ).rejects.toThrow("not confirmed");
});
it("does not retry deletion conflicts or lost responses", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(new Response(null, { status: 409 }))
    .mockRejectedValueOnce(new TypeError("Disconnected"));
  vi.stubGlobal("fetch", fetch);
  const article = { ...item, content: null };
  await expect(
    libraryApi.deleteArticle!(article, new AbortController().signal),
  ).rejects.toThrow("confirm deletion again");
  await expect(
    libraryApi.deleteArticle!(article, new AbortController().signal),
  ).rejects.toThrow("not confirmed");
  expect(fetch).toHaveBeenCalledTimes(2);
});
describe("library HTTP client", () => {
  it("sends literal filters safely and preserves the article version in metadata updates", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ items: [], nextCursor: null }))
      .mockResolvedValueOnce(
        Response.json({
          ...item,
          content: null,
          category: "BOOKS",
          entityName: null,
          version: 4,
        }),
      )
      .mockResolvedValueOnce(
        Response.json({ reason: "VERSION_CONFLICT" }, { status: 409 }),
      );
    vi.stubGlobal("fetch", fetch);
    const signal = new AbortController().signal;
    await libraryApi.list(null, signal, {
      category: "BOOKS",
      query: "100% & title",
    });
    expect(fetch.mock.calls[0]![0]).toBe(
      "/api/v1/articles?limit=20&category=BOOKS&query=100%25+%26+title",
    );
    const article = { ...item, content: null, version: 3 };
    await libraryApi.updateMetadata!(article, "BOOKS", null, signal);
    expect(JSON.parse(fetch.mock.calls[1]![1].body)).toEqual({
      version: 3,
      category: "BOOKS",
      entityName: null,
    });
    await expect(
      libraryApi.updateMetadata!(article, "BOOKS", null, signal),
    ).rejects.toThrow("Reopen");
    expect(fetch).toHaveBeenCalledTimes(3);
  });
  it("requires the contracted 200 status and handles unreadable responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(
          Response.json({ items: [], nextCursor: null }, { status: 202 }),
        )
        .mockResolvedValueOnce(new Response("not JSON", { status: 200 })),
    );
    await expect(
      libraryApi.list(null, new AbortController().signal),
    ).rejects.toThrow("unexpected response");
    await expect(
      libraryApi.list(null, new AbortController().signal),
    ).rejects.toThrow("unreadable response");
  });
  it("sends opaque cursors unchanged and uses the current server origin", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(Response.json({ items: [item], nextCursor: null }));
    vi.stubGlobal("fetch", fetch);
    await expect(
      libraryApi.list("opaque+/bookmark", new AbortController().signal),
    ).resolves.toEqual({ items: [item], nextCursor: null });
    expect(fetch.mock.calls[0]![0]).toBe(
      "/api/v1/articles?limit=20&cursor=opaque%2B%2Fbookmark",
    );
    expect(fetch.mock.calls[0]![1].credentials).toBe("same-origin");
  });
  it("reads nullable content and rejects invalid success data", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ ...item, content: null }))
      .mockResolvedValueOnce(
        Response.json({
          items: [{ ...item, url: "javascript:evil()" }],
          nextCursor: null,
        }),
      );
    vi.stubGlobal("fetch", fetch);
    expect(
      (await libraryApi.read(item.id, new AbortController().signal)).content,
    ).toBeNull();
    await expect(
      libraryApi.list(null, new AbortController().signal),
    ).rejects.toThrow("unexpected");
  });
  it("handles missing articles and HTTP failures before parsing success data", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("Not found", { status: 404 })),
    );
    await expect(
      libraryApi.read(item.id, new AbortController().signal),
    ).rejects.toThrow("no longer available");
  });
  it("does not detach native fetch from its window context", async () => {
    vi.stubGlobal("fetch", function (this: unknown) {
      expect(this).toBe(globalThis);
      return Promise.resolve(Response.json({ items: [], nextCursor: null }));
    });
    await expect(
      libraryApi.list(null, new AbortController().signal),
    ).resolves.toMatchObject({ items: [] });
  });
});
