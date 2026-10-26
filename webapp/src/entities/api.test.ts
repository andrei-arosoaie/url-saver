import { afterEach, expect, it, vi } from "vitest";
import { entityApi, EntityApiError } from "./api";
import {
  types,
  entityHash,
  entityRoute,
  fromUtcInput,
  type Entity,
} from "./model";
const entity: Entity = {
  id: "00000000-0000-4000-8000-000000000001",
  type: "BOOK",
  title: " Exact title ",
  status: "TO_READ",
  createdAt: "2026-10-25T18:00:00Z",
  version: 3,
  totalCompletionCount: 0,
  lastCompletedAt: null,
};
const signal = () => new AbortController().signal;
afterEach(() => vi.unstubAllGlobals());
it("preserves literal filters and opaque cursors without detaching fetch", async () => {
  const calls: unknown[][] = [];
  vi.stubGlobal("fetch", function (this: unknown, ...args: unknown[]) {
    expect(this).toBe(globalThis);
    calls.push(args);
    return Promise.resolve(
      Response.json({
        items: [
          { ...entity, completionCount: 0, latestMatchingCompletionAt: null },
        ],
        nextCursor: null,
      }),
    );
  });
  await entityApi.list(
    { type: "BOOK", query: "100% & exact", completionYear: 2025 },
    "opaque+/bookmark",
    signal(),
  );
  expect(calls[0]?.[0]).toBe(
    "/api/v1/entities?type=BOOK&query=100%25+%26+exact&completionYear=2025&cursor=opaque%2B%2Fbookmark",
  );
});
it("sends exact titles and uses entity versions for completion and update", async () => {
  const event = {
    id: "event",
    entityId: entity.id,
    version: 2,
    completedAt: "2025-01-01T00:00:00.123456Z",
    createdAt: "2026-10-25T18:00:00Z",
  };
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(Response.json(entity, { status: 201 }))
    .mockResolvedValueOnce(Response.json(entity))
    .mockResolvedValueOnce(
      Response.json({ entity, completion: event }, { status: 201 }),
    )
    .mockResolvedValueOnce(Response.json(event))
    .mockResolvedValueOnce(new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetch);
  await entityApi.create({ type: "BOOK", title: " Exact title " }, signal());
  await entityApi.update(entity, { title: " Revised " }, signal());
  await entityApi.complete(entity, undefined, signal());
  await entityApi.correct(event, "2025-02-01T00:00:00.123456Z", signal());
  await entityApi.deleteCompletion(event, signal());
  expect(JSON.parse(fetch.mock.calls[0]![1].body)).toEqual({
    type: "BOOK",
    title: " Exact title ",
  });
  expect(JSON.parse(fetch.mock.calls[1]![1].body)).toEqual({
    version: 3,
    title: " Revised ",
  });
  expect(JSON.parse(fetch.mock.calls[2]![1].body)).toEqual({ version: 3 });
  expect(JSON.parse(fetch.mock.calls[3]![1].body)).toEqual({
    version: 2,
    completedAt: "2025-02-01T00:00:00.123456Z",
  });
  expect(fetch.mock.calls[4]![0]).toContain("?version=2");
});
it("rejects unknown success statuses, invalid statuses, and malformed list bodies", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      Response.json({ items: [], nextCursor: null }, { status: 202 }),
    )
    .mockResolvedValueOnce(
      Response.json({
        items: [
          {
            ...entity,
            status: "SEEN",
            completionCount: 0,
            latestMatchingCompletionAt: null,
          },
        ],
        nextCursor: null,
      }),
    )
    .mockResolvedValueOnce(
      Response.json({ items: [entity], nextCursor: null }),
    );
  vi.stubGlobal("fetch", fetch);
  for (let i = 0; i < 3; i++)
    await expect(
      entityApi.list({ type: "BOOK" }, null, signal()),
    ).rejects.toBeInstanceOf(EntityApiError);
});
it("does not retry conflicts or unconfirmed writes and exposes the explicit reload requirement", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      Response.json({ reason: "VERSION_CONFLICT" }, { status: 409 }),
    )
    .mockRejectedValueOnce(new TypeError("Network lost"));
  vi.stubGlobal("fetch", fetch);
  await expect(
    entityApi.update(entity, { status: "READ" }, signal()),
  ).rejects.toMatchObject({ reason: "VERSION_CONFLICT", status: 409 });
  await expect(
    entityApi.complete(entity, undefined, signal()),
  ).rejects.toMatchObject({ uncertain: true, status: 0 });
  expect(fetch).toHaveBeenCalledTimes(2);
});
it("keeps exact UTC microseconds and route filters through view/detail navigation", () => {
  const route = {
    type: "TV_SHOW" as const,
    view: "year" as const,
    id: entity.id,
    query: "A & B",
    year: "2025",
  };
  expect(entityRoute(entityHash(route))).toEqual(route);
  expect(fromUtcInput("2025-12-31T23:59:59.123456")).toBe(
    "2025-12-31T23:59:59.123456Z",
  );
  expect(Object.keys(types)).toHaveLength(4);
  expect(() => fromUtcInput("2025-02-30T12:00:00")).toThrow();
  expect(entityRoute("#entities/__proto__/table").type).toBe("BOOK");
});
