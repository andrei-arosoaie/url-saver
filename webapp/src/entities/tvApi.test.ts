import { afterEach, expect, it, vi } from "vitest";
import { tvRequest } from "./tvApi";
import { EntityApiError, requiresReload } from "./api";
afterEach(() => vi.unstubAllGlobals());
it("rejects malformed successful data and treats malformed mutations as unconfirmed", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response('{"items":[]}')),
  );
  await expect(
    tvRequest("/tvmaze/shows?query=show", new AbortController().signal),
  ).rejects.toBeInstanceOf(EntityApiError);
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}")));
  const failure = await tvRequest(
    "/entities/id/tv-show/position",
    new AbortController().signal,
    "PATCH",
    { version: 0 },
  ).catch((error) => error);
  expect(requiresReload(failure)).toBe(true);
});
it("preserves version conflicts and duplicate-provider identity for recovery", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ reason: "VERSION_CONFLICT" }), {
        status: 409,
      }),
    ),
  );
  const conflict = await tvRequest(
    "/entities/id/tv-show",
    new AbortController().signal,
    "PATCH",
    { alias: "Label", version: 0 },
  ).catch((error) => error);
  expect(requiresReload(conflict)).toBe(true);
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          reason: "DUPLICATE_ENTITY",
          existingEntityId: "owned-id",
          message: "Already added",
        }),
        { status: 409 },
      ),
    ),
  );
  await expect(
    tvRequest("/tvmaze/entities", new AbortController().signal, "POST", {
      tvmazeId: 1,
    }),
  ).rejects.toMatchObject({
    reason: "DUPLICATE_ENTITY",
    existingEntityId: "owned-id",
  });
});
