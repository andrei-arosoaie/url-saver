import { afterEach, expect, it, vi } from "vitest";
import { categoryApi } from "./api";
const record = {
  key: "stable-key",
  displayName: "Research",
  color: "#112233",
  enabled: true,
  isDefault: false,
  createdAt: "2026-10-30T00:00:00Z",
  version: 2,
  articleCount: 3,
};
const signal = () => new AbortController().signal;
afterEach(() => vi.unstubAllGlobals());
it("uses same-origin category contracts and preserves stable keys and versions", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(Response.json({ items: [record] }))
    .mockResolvedValueOnce(
      Response.json(
        { ...record, version: 0, articleCount: 0 },
        { status: 201 },
      ),
    )
    .mockResolvedValueOnce(
      Response.json({ ...record, displayName: "Reading", version: 3 }),
    )
    .mockResolvedValueOnce(new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetch);
  expect(await categoryApi.list(signal())).toEqual([record]);
  await categoryApi.create("Research", "#112233", signal());
  await categoryApi.edit(record, { displayName: "Reading" }, signal());
  await categoryApi.remove(record, signal());
  expect(fetch.mock.calls.map((c) => c[0])).toEqual([
    "/api/v1/categories",
    "/api/v1/categories",
    "/api/v1/categories/stable-key",
    "/api/v1/categories/stable-key?version=2",
  ]);
  expect(JSON.parse(fetch.mock.calls[2]![1].body)).toEqual({
    version: 2,
    displayName: "Reading",
  });
  expect(
    fetch.mock.calls.every((c) => c[1].credentials === "same-origin"),
  ).toBe(true);
});
it("distinguishes conflicts and never retries a mutation automatically", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      Response.json({ reason: "CATEGORY_IN_USE" }, { status: 409 }),
    )
    .mockResolvedValueOnce(
      Response.json({ reason: "VERSION_CONFLICT" }, { status: 409 }),
    )
    .mockResolvedValueOnce(
      Response.json({ reason: "DUPLICATE_CATEGORY_NAME" }, { status: 409 }),
    );
  vi.stubGlobal("fetch", fetch);
  await expect(categoryApi.remove(record, signal())).rejects.toThrow(
    "Remove or move",
  );
  await expect(
    categoryApi.edit(record, { enabled: false }, signal()),
  ).rejects.toThrow("Reload categories");
  await expect(
    categoryApi.create("Research", "#112233", signal()),
  ).rejects.toThrow("already have");
  expect(fetch).toHaveBeenCalledTimes(3);
});
it("rejects unexpected statuses and invalid counts instead of displaying false success", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValueOnce(Response.json(record, { status: 202 }))
      .mockResolvedValueOnce(
        Response.json({ items: [{ ...record, articleCount: -1 }] }),
      ),
  );
  await expect(
    categoryApi.create("Research", "#112233", signal()),
  ).rejects.toThrow();
  await expect(categoryApi.list(signal())).rejects.toThrow(
    "unexpected category",
  );
});
it("requires reloading after an unconfirmed request", async () => {
  const fetch = vi.fn().mockRejectedValue(new TypeError("Network lost"));
  vi.stubGlobal("fetch", fetch);
  await expect(categoryApi.remove(record, signal())).rejects.toThrow(
    "not confirmed",
  );
  expect(fetch).toHaveBeenCalledTimes(1);
});
