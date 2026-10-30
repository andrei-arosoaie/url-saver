import { beforeEach, afterEach, expect, it, vi } from "vitest";
const initialTime = Date.parse("2026-10-30T00:00:00Z");
let now = initialTime;
beforeEach(() => {
  vi.resetModules();
  now = initialTime;
  vi.spyOn(Date, "now").mockImplementation(() => now);
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
const image = (signature: string, version = 1) => ({
  status: "AVAILABLE",
  version,
  url: `https://images.test/file.jpg?ik-t=1&ik-s=${signature}`,
  expiresAt: new Date(now + 1800000).toISOString(),
});
const response = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status });
const signal = () => new AbortController().signal;

it("reuses the image URL while still rechecking ownership and screenshot version on every read", async () => {
  const first = image("first");
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(response(first))
    .mockResolvedValueOnce(response(image("new-signature")));
  vi.stubGlobal("fetch", fetch);
  const { readScreenshot } = await import("./api");
  expect((await readScreenshot("one", signal())).url).toBe(first.url);
  expect((await readScreenshot("one", signal())).url).toBe(first.url);
  expect(fetch).toHaveBeenCalledTimes(2);
  expect(fetch.mock.calls[1]![1]).toMatchObject({
    cache: "no-store",
    credentials: "same-origin",
  });
});

it("replaces cached URLs for a new screenshot version and clears them when an image is missing", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(response(image("first")))
    .mockResolvedValueOnce(response(image("replacement", 2)))
    .mockResolvedValueOnce(response({ status: "MISSING", version: 3 }))
    .mockResolvedValueOnce(response(image("restored", 3)));
  vi.stubGlobal("fetch", fetch);
  const { readScreenshot } = await import("./api");
  await readScreenshot("one", signal());
  expect((await readScreenshot("one", signal())).url).toContain("replacement");
  expect((await readScreenshot("one", signal())).status).toBe("MISSING");
  expect((await readScreenshot("one", signal())).url).toContain("restored");
});

it("renews near expiry and forces both browser URL and backend URL caches to refresh after an image error", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(response(image("first")))
    .mockImplementationOnce(async () => response(image("renewed")))
    .mockImplementationOnce(async () => response(image("forced")));
  vi.stubGlobal("fetch", fetch);
  const { readScreenshot } = await import("./api");
  await readScreenshot("one", signal());
  now += 1800000 - 4000;
  expect((await readScreenshot("one", signal())).url).toContain("renewed");
  expect((await readScreenshot("one", signal(), true)).url).toContain("forced");
  expect(fetch.mock.calls[2]![0]).toBe(
    "/api/v1/articles/one/screenshot?refresh=true",
  );
});

it("never serves a cached private image after the backend denies access", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(response(image("first")))
    .mockResolvedValueOnce(response({}, 404))
    .mockResolvedValueOnce(response(image("reauthorized")));
  vi.stubGlobal("fetch", fetch);
  const { readScreenshot } = await import("./api");
  await readScreenshot("one", signal());
  await expect(readScreenshot("one", signal())).rejects.toThrow(
    "Screenshot unavailable",
  );
  expect((await readScreenshot("one", signal())).url).toContain("reauthorized");
});
