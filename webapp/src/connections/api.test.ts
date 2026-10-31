import { afterEach, expect, it, vi } from "vitest";
import { connectionsApi } from "./api";
import { complete, sourceId, related } from "./fixtures";
afterEach(() => vi.unstubAllGlobals());
it("checks exact statuses, encodes paths and cursors, and binds fetch to the global object", async () => {
  const fetch = vi.fn(function (this: unknown) {
    expect(this).toBe(globalThis);
    return Promise.resolve(Response.json(complete));
  });
  vi.stubGlobal("fetch", fetch);
  const signal = new AbortController().signal;
  expect(await connectionsApi.list(sourceId, "a/b?c", signal)).toEqual(
    complete,
  );
  expect(fetch.mock.calls[0]).toBeTruthy();
  expect(fetch).toHaveBeenLastCalledWith(
    `/api/v1/articles/${sourceId}/connections?cursor=a%2Fb%3Fc`,
    expect.objectContaining({ credentials: "same-origin", method: "GET" }),
  );
  fetch.mockResolvedValueOnce(new Response(null, { status: 202 }));
  await connectionsApi.retry("a/b", signal);
  expect(fetch).toHaveBeenLastCalledWith(
    "/api/v1/articles/a%2Fb/connections/retry",
    expect.objectContaining({ method: "POST" }),
  );
  fetch.mockResolvedValueOnce(new Response(null, { status: 204 }));
  await connectionsApi.remove(sourceId, related.id, signal);
  expect(fetch).toHaveBeenLastCalledWith(
    `/api/v1/articles/${sourceId}/connections/${related.id}`,
    expect.objectContaining({ method: "DELETE" }),
  );
  await expect(connectionsApi.retry(sourceId, signal)).rejects.toThrow(
    "not confirmed",
  );
  expect(fetch).toHaveBeenCalledTimes(4);
});
it("rejects incomplete or unsafe pages rather than displaying malformed results", async () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  for (const data of [
    {},
    { ...complete, items: [{ ...related, url: "javascript:alert(1)" }] },
    { ...complete, items: [{ ...related, semanticScore: 2 }] },
    { ...complete, items: [{ ...related, savedAt: "bad" }] },
    { ...complete, processing: { ...complete.processing, pollIntervalMs: 0 } },
    { ...complete, processing: { ...complete.processing, state: "UNKNOWN" } },
    { ...complete, nextCursor: 2 },
    {
      ...complete,
      items: [{ ...related, sharedEntities: [], semanticScore: null }],
    },
  ]) {
    fetch.mockResolvedValueOnce(Response.json(data));
    await expect(
      connectionsApi.list(sourceId, null, new AbortController().signal),
    ).rejects.toThrow("unreadable");
  }
});
it("preserves aborts and reports missing articles, disabled work and uncertain writes without retrying", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(new Response(null, { status: 404 }))
    .mockResolvedValueOnce(new Response(null, { status: 409 }))
    .mockRejectedValueOnce(new Error("offline"));
  vi.stubGlobal("fetch", fetch);
  const signal = new AbortController().signal;
  await expect(connectionsApi.list(sourceId, null, signal)).rejects.toThrow(
    "no longer available",
  );
  await expect(connectionsApi.retry(sourceId, signal)).rejects.toThrow(
    "disabled",
  );
  await expect(
    connectionsApi.remove(sourceId, related.id, signal),
  ).rejects.toThrow("not confirmed");
  expect(fetch).toHaveBeenCalledTimes(3);
});
