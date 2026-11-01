import { beforeEach, expect, test, vi } from "vitest";
const fixture = vi.hoisted(() => ({ records: {} as Record<string, unknown> }));
vi.mock("wxt/browser", () => ({
  browser: {
    storage: {
      local: {
        get: async (key: string | null) =>
          key === null
            ? { ...fixture.records }
            : { [key]: fixture.records[key] },
        set: async (value: Record<string, unknown>) => {
          Object.assign(fixture.records, value);
        },
        remove: async (key: string) => {
          delete fixture.records[key];
        },
      },
    },
  },
}));
import { sessionQueueStorage } from "./sessionQueueStorage";
beforeEach(() => {
  fixture.records = {};
});
test("an old worker cannot overwrite, restore or remove another session's job for the same URL", async () => {
  const old = sessionQueueStorage<{ authSession?: string; content: string }>(
    "save:",
    Promise.resolve("old"),
  );
  const current = sessionQueueStorage<{
    authSession?: string;
    content: string;
  }>("save:", Promise.resolve("current"));
  await old.set("save:url", { content: "old article" });
  expect(await current.get("save:url")).toBeUndefined();
  expect(await current.all()).toEqual({});
  await current.set("save:url", { content: "new article" });
  await old.set("save:url", { content: "late old worker" });
  await old.remove("save:url");
  expect(await current.get("save:url")).toMatchObject({
    content: "new article",
    authSession: "current",
  });
  expect(await current.all()).toEqual({
    "save:url": { content: "new article", authSession: "current" },
  });
});
test("unbound legacy and anonymous jobs cannot resume into a signed-in session", async () => {
  fixture.records["save:url"] = { content: "legacy anonymous article" };
  const current = sessionQueueStorage<{ authSession?: string }>(
    "save:",
    Promise.resolve("current"),
  );
  const anonymous = sessionQueueStorage<{ authSession?: string }>(
    "save:",
    Promise.resolve(undefined),
  );
  expect(await current.get("save:url")).toBeUndefined();
  expect(await current.all()).toEqual({});
  expect(await anonymous.all()).toEqual({});
});
