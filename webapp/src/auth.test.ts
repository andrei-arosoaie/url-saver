import { afterEach, beforeEach, expect, test, vi } from "vitest";

const originalFetch = globalThis.fetch;
const jwt = (sid = "session-one") =>
  "header." + btoa(JSON.stringify({ sid })) + ".signature";
beforeEach(() => {
  vi.resetModules();
  history.replaceState(null, "", "/");
  sessionStorage.clear();
  vi.stubGlobal("BroadcastChannel", undefined);
});
afterEach(() => {
  vi.unstubAllGlobals();
  globalThis.fetch = originalFetch;
});
test("coordinates concurrent refreshes and keeps bearer tokens off other origins", async () => {
  let grants = 0;
  const requests: Request[] = [];
  const fetch = vi.fn(async (input: RequestInfo | URL) => {
    const url =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.href
          : input.url;
    if (url === "/oauth2/csrf") return Response.json({ csrf_token: "csrf" });
    if (url === "/oauth2/token") {
      grants++;
      await new Promise((resolve) => setTimeout(resolve, 10));
      return Response.json({ access_token: jwt(), expires_in: 86400 });
    }
    if (input instanceof Request) requests.push(input);
    return Response.json({});
  });
  vi.stubGlobal("fetch", fetch);
  const auth = await import("./auth");
  auth.installAuthenticatedFetch();
  await Promise.all([
    globalThis.fetch("/api/v1/articles"),
    globalThis.fetch("/api/v1/categories"),
  ]);
  expect(grants).toBe(1);
  expect(
    requests.map((request) => request.headers.get("Authorization")),
  ).toEqual([`Bearer ${jwt()}`, `Bearer ${jwt()}`]);
  await globalThis.fetch("https://other.example/api/v1/articles");
  expect(fetch.mock.calls.at(-1)?.[0]).toBe(
    "https://other.example/api/v1/articles",
  );
  expect(sessionStorage.length).toBe(0);
});
test("rejects a mismatched callback state and removes the authorization code from the URL", async () => {
  history.replaceState(null, "", "/auth/callback?code=secret&state=attacker");
  sessionStorage.setItem(
    "know-pkce",
    JSON.stringify({
      state: "expected",
      verifier: "verifier",
      createdAt: Date.now(),
    }),
  );
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  const auth = await import("./auth");
  await expect(auth.initializeAuthentication()).rejects.toThrow(
    "could not be verified",
  );
  expect(location.pathname).toBe("/");
  expect(location.search).toBe("");
  expect(sessionStorage.getItem("know-pkce")).toBeNull();
  expect(fetch).not.toHaveBeenCalled();
});
test("code exchange includes PKCE and CSRF without storing tokens in web storage", async () => {
  history.replaceState(null, "", "/auth/callback?code=once&state=expected");
  sessionStorage.setItem(
    "know-pkce",
    JSON.stringify({
      state: "expected",
      verifier: "original-verifier",
      createdAt: Date.now(),
    }),
  );
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (input === "/oauth2/csrf")
      return Response.json({ csrf_token: "csrf-value" });
    expect(
      new URLSearchParams(init?.body as URLSearchParams).get("code_verifier"),
    ).toBe("original-verifier");
    expect(new Headers(init?.headers).get("X-Know-CSRF")).toBe("csrf-value");
    expect(init?.credentials).toBe("same-origin");
    return Response.json({ access_token: jwt(), expires_in: 86400 });
  });
  vi.stubGlobal("fetch", fetch);
  const auth = await import("./auth");
  expect(await auth.initializeAuthentication()).toBe(true);
  expect(sessionStorage.length).toBe(0);
});
test("a pending write is never retried under another tab's sign-in session", async () => {
  let grants = 0;
  const writes: Request[] = [];
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (input === "/oauth2/csrf") return Response.json({ csrf_token: "csrf" });
    if (input === "/oauth2/token") {
      grants++;
      if (grants === 2)
        expect(
          new URLSearchParams(init?.body as URLSearchParams).get(
            "expected_session",
          ),
        ).toBe("session-one");
      return Response.json({
        access_token: jwt(grants === 1 ? "session-one" : "session-two"),
        expires_in: 86400,
      });
    }
    if (input instanceof Request) writes.push(input);
    return new Response(null, { status: 401 });
  });
  vi.stubGlobal("fetch", fetch);
  const auth = await import("./auth");
  expect(await auth.initializeAuthentication()).toBe(true);
  auth.installAuthenticatedFetch();
  const response = await globalThis.fetch("/api/v1/articles", {
    method: "POST",
    body: "original-account-write",
  });
  expect(response.status).toBe(401);
  expect(writes).toHaveLength(1);
  expect(writes[0].headers.get("Authorization")).toBe(`Bearer ${jwt()}`);
  await expect(globalThis.fetch("/api/v1/articles")).rejects.toThrow(
    "sign-in changed",
  );
});
