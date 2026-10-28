import { describe, expect, it, vi } from "vitest";
import { ArticleApi } from "./api";
import { articleHandler } from "./messages";
import { SaveQueue } from "./saveQueue";
const capture = {
  url: "https://example.com/",
  title: "Example",
  content: "<p>Article</p>",
};
describe("extension message boundary", () => {
  it("checks the real tab URL before reading or acknowledging stored captures", async () => {
    const queue = {
      status: vi.fn(),
      acknowledge: vi.fn(),
      start: vi.fn(),
    } as unknown as SaveQueue;
    const api = new ArticleApi(
      "https://know.example",
      async () => undefined,
      vi.fn(),
    );
    const handler = articleHandler(
      api,
      async () => ({ active: true, url: "https://other.test/" }),
      queue,
    );
    for (const action of ["status", "acknowledge", "save"]) {
      expect(
        await handler(
          { type: "know:article", action, capture },
          { tab: { id: 1 }, frameId: 0 },
        ),
      ).toMatchObject({ ok: false });
    }
    expect(queue.status).not.toHaveBeenCalled();
    expect(queue.acknowledge).not.toHaveBeenCalled();
    expect(queue.start).not.toHaveBeenCalled();
  });
  function fixture(active = true, url = capture.url) {
    const request = vi.fn<typeof fetch>();
    const api = new ArticleApi(
      "https://know.example",
      async () => "test-only-token",
      request,
    );
    const handler = articleHandler(api, async () => ({ active, url }));
    return { request, handler };
  }
  it.each([
    { tab: { id: 1 }, frameId: 1 },
    { tab: { id: 1, incognito: true }, frameId: 0 },
    { frameId: 0 },
  ])("ignores forbidden sender %j", async (sender) => {
    const { handler, request } = fixture();
    expect(
      await handler({ type: "know:article", action: "save", capture }, sender),
    ).toBeUndefined();
    expect(request).not.toHaveBeenCalled();
  });
  it("rejects inactive tabs and stale page captures before transmission", async () => {
    for (const f of [
      fixture(false),
      fixture(true, "https://different.example/"),
    ]) {
      expect(
        await f.handler(
          { type: "know:article", action: "save", capture },
          { tab: { id: 1 }, frameId: 0 },
        ),
      ).toMatchObject({ ok: false });
      expect(f.request).not.toHaveBeenCalled();
    }
  });
  it("carries a confirmed response back to the UI", async () => {
    const { handler, request } = fixture();
    request.mockResolvedValue(
      Response.json(
        {
          outcome: "CREATED",
          article: {
            ...capture,
            id: "id",
            version: 0,
            savedAt: "2026-10-25T00:00:00Z",
          },
        },
        { status: 201 },
      ),
    );
    expect(
      await handler(
        { type: "know:article", action: "save", capture },
        { tab: { id: 1 }, frameId: 0 },
      ),
    ).toMatchObject({ ok: true, data: { outcome: "CREATED" } });
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("passes duplicate identification back without updating", async () => {
    const { handler, request } = fixture();
    request.mockResolvedValue(
      Response.json(
        { reason: "DUPLICATE_URL", existingArticleId: "existing" },
        { status: 409 },
      ),
    );
    expect(
      await handler(
        { type: "know:article", action: "save", capture },
        { tab: { id: 1 }, frameId: 0 },
      ),
    ).toMatchObject({
      ok: false,
      status: 409,
      reason: "DUPLICATE_URL",
      id: "existing",
    });
    expect(request).toHaveBeenCalledTimes(1);
  });
});
