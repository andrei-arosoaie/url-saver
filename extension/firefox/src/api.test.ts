import { describe, expect, it, vi } from "vitest";
import { ArticleApi, ServerNonResponse } from "./api";
const article = {
  id: "5f1c60e4-cb46-444c-ad37-729109cf6d01",
  url: "https://example.com/",
  title: "Article",
  content: "<p>Text</p>",
  version: 0,
  savedAt: "2026-10-25T00:00:00Z",
};
const capture = {
  url: article.url,
  title: article.title,
  content: article.content,
};
function fixture() {
  const request = vi.fn<typeof fetch>();
  return {
    request,
    api: new ArticleApi("https://know.example", async () => "secret", request),
  };
}
describe("article API", () => {
  it.each(["TypeError", "TimeoutError", "AbortError", "NetworkError"])(
    "classifies transport %s failures across browser realms for background retries",
    async (name) => {
      const { request, api } = fixture();
      request.mockRejectedValue({ name });
      await expect(api.save(capture)).rejects.toBeInstanceOf(ServerNonResponse);
      expect(request).toHaveBeenCalledTimes(1);
    },
  );
  it("treats an interrupted successful response body as uncertain but never retries a known HTTP error", async () => {
    const { request, api } = fixture();
    request.mockResolvedValue({
      ok: true,
      status: 201,
      json: () => Promise.reject({ name: "AbortError" }),
    } as unknown as Response);
    await expect(api.save(capture)).rejects.toBeInstanceOf(ServerNonResponse);
    request.mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.reject({ name: "AbortError" }),
    } as unknown as Response);
    await expect(api.save(capture)).rejects.toMatchObject({ status: 401 });
  });
  it("binds native fetch to its global context rather than the API instance", async () => {
    const nativeFetch = globalThis.fetch;
    let calls = 0;
    globalThis.fetch = function (this: unknown) {
      expect(this).toBe(globalThis);
      calls++;
      return Promise.resolve(
        Response.json({ outcome: "CREATED", article }, { status: 201 }),
      );
    };
    try {
      const api = new ArticleApi("https://know.example", async () => undefined);
      await expect(api.save(capture)).resolves.toMatchObject({
        outcome: "CREATED",
      });
      expect(calls).toBe(1);
    } finally {
      globalThis.fetch = nativeFetch;
    }
  });
  it("waits for a valid server response and sends bearer credentials only to configured server", async () => {
    const { request, api } = fixture();
    request.mockResolvedValue(
      Response.json({ outcome: "CREATED", article }, { status: 201 }),
    );
    await expect(api.save(capture)).resolves.toEqual({
      outcome: "CREATED",
      article,
    });
    const [url, options] = request.mock.calls[0]!;
    expect(String(url)).toBe("https://know.example/api/v1/articles");
    expect(options?.headers).toMatchObject({ Authorization: "Bearer secret" });
    expect(options?.credentials).toBe("omit");
    expect(options?.redirect).toBe("error");
    expect(options?.signal).toBeInstanceOf(AbortSignal);
  });
  it("saves without an access token using the server temporary account", async () => {
    const request = vi.fn();
    request.mockResolvedValue(
      Response.json({ outcome: "CREATED", article }, { status: 201 }),
    );
    const api = new ArticleApi(
      "https://know.example",
      async () => undefined,
      request,
    );
    await expect(api.save(capture)).resolves.toMatchObject({
      outcome: "CREATED",
    });
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0]![1].headers).not.toHaveProperty(
      "Authorization",
    );
  });
  it("surfaces duplicates without an automatic update or retry", async () => {
    const { request, api } = fixture();
    request.mockResolvedValue(
      Response.json(
        { reason: "DUPLICATE_URL", existingArticleId: article.id },
        { status: 409 },
      ),
    );
    await expect(api.save(capture)).rejects.toMatchObject({
      status: 409,
      reason: "DUPLICATE_URL",
      existingArticleId: article.id,
    });
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("uses existing version on explicit update without changing URL", async () => {
    const { request, api } = fixture();
    request.mockResolvedValue(Response.json({ ...article, version: 3 }));
    await api.update(article.id, 2, capture);
    expect(JSON.parse(request.mock.calls[0]![1]!.body as string)).toEqual({
      version: 2,
      title: capture.title,
      content: capture.content,
    });
  });
  it("rejects invalid success payloads and inappropriate status codes", async () => {
    const { request, api } = fixture();
    request.mockResolvedValue(
      Response.json({ outcome: "CREATED", article: {} }),
    );
    await expect(api.save(capture)).rejects.toThrow("Unexpected");
    request.mockResolvedValue(
      Response.json(
        {
          outcome: "CREATED",
          article: { ...article, url: "https://different.test/" },
        },
        { status: 201 },
      ),
    );
    await expect(api.save(capture)).rejects.toThrow("Unexpected");
    request.mockResolvedValue(
      Response.json({ outcome: "CREATED", article }, { status: 202 }),
    );
    await expect(api.save(capture)).rejects.toThrow("Unexpected");
  });
  it("aborts at 60 seconds and never retries automatically", async () => {
    vi.useFakeTimers();
    const signalFactory = vi.spyOn(AbortSignal, "timeout");
    const { request, api } = fixture();
    request.mockRejectedValue(new DOMException("Timed out", "TimeoutError"));
    await expect(api.save(capture)).rejects.toBeInstanceOf(ServerNonResponse);
    expect(signalFactory).toHaveBeenCalledWith(60_000);
    expect(request).toHaveBeenCalledTimes(1);
    vi.useRealTimers();
  });
});
