import { afterEach, expect, it, vi } from "vitest";
import { identificationApi } from "./api";
const status = {
  status: "SUCCEEDED",
  reason: null,
  provider: "openrouter",
  model: "openrouter/free",
  subjects: [{ name: "1984", main: true }],
  categorySource: "AI",
  nameSource: "AI",
  requestedAt: "2026-10-31T10:00:00Z",
  completedAt: "2026-10-31T10:00:05Z",
};
afterEach(() => vi.unstubAllGlobals());
it("requires exactly GET 200 and POST 202 and never retries a request", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(Response.json(status))
    .mockResolvedValueOnce(
      Response.json({ ...status, status: "PENDING" }, { status: 202 }),
    )
    .mockResolvedValueOnce(Response.json(status));
  vi.stubGlobal("fetch", fetch);
  const signal = new AbortController().signal;
  expect(await identificationApi.status("a/b", signal)).toEqual(status);
  await identificationApi.request("a/b", signal);
  expect(fetch.mock.calls[0][0]).toBe("/api/v1/articles/a%2Fb/identification");
  expect(fetch.mock.calls[1][1].method).toBe("POST");
  expect(fetch.mock.calls[1][1].credentials).toBe("same-origin");
  await expect(identificationApi.request("a/b", signal)).rejects.toThrow(
    "not confirmed",
  );
  expect(fetch).toHaveBeenCalledTimes(3);
});
it("rejects malformed statuses, provenance, timestamps and ambiguous main subjects", async () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  for (const invalid of [
    { ...status, status: "DONE" },
    { ...status, nameSource: "maybe" },
    { ...status, completedAt: "bad" },
    {
      ...status,
      subjects: [
        { name: "A", main: true },
        { name: "B", main: true },
      ],
    },
    { ...status, subjects: [{ name: " ", main: true }] },
  ]) {
    fetch.mockResolvedValueOnce(Response.json(invalid));
    await expect(
      identificationApi.status("a", new AbortController().signal),
    ).rejects.toThrow("unreadable");
  }
});
it("reports missing articles, disabled identification and uncertain network outcomes", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(new Response(null, { status: 404 }))
    .mockResolvedValueOnce(new Response(null, { status: 503 }))
    .mockRejectedValueOnce(new TypeError("Offline"));
  vi.stubGlobal("fetch", fetch);
  const signal = new AbortController().signal;
  await expect(identificationApi.status("a", signal)).rejects.toThrow(
    "no longer available",
  );
  await expect(identificationApi.request("a", signal)).rejects.toThrow(
    "unavailable",
  );
  await expect(identificationApi.request("a", signal)).rejects.toThrow(
    "Refresh status",
  );
  expect(fetch).toHaveBeenCalledTimes(3);
});
