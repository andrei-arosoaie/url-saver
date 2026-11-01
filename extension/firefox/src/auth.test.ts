import { beforeEach, expect, test, vi } from "vitest";
const fixture = vi.hoisted(() => ({
  records: {} as Record<string, unknown>,
  launch: vi.fn(),
  reload: vi.fn(),
}));
vi.mock("wxt/browser", () => ({
  browser: {
    storage: {
      local: {
        get: async (key: string) => ({ [key]: fixture.records[key] }),
        set: async (value: Record<string, unknown>) => {
          Object.assign(fixture.records, value);
        },
        remove: async (key: string) => {
          delete fixture.records[key];
        },
      },
    },
    identity: {
      getRedirectURL: () => "https://fixture.extensions.allizom.org/",
      launchWebAuthFlow: fixture.launch,
    },
    runtime: { reload: fixture.reload },
  },
}));
const jwt =
  "header." + btoa(JSON.stringify({ sid: "session-one" })) + ".signature";
test("sign-in binds state and PKCE, stores credentials, and never saves or reloads the page", async () => {
  fixture.launch.mockImplementation(async ({ url }: { url: string }) => {
    const request = new URL(url);
    expect(request.searchParams.get("code_challenge_method")).toBe("S256");
    return (
      "https://fixture.extensions.allizom.org/?code=once&state=" +
      request.searchParams.get("state")
    );
  });
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    expect(String(input)).toContain("/oauth2/token");
    expect(init?.credentials).toBe("omit");
    const form = new URLSearchParams(init?.body as URLSearchParams);
    expect(form.get("grant_type")).toBe("authorization_code");
    expect(form.get("code")).toBe("once");
    expect(form.get("code_verifier")).toMatch(/^[A-Za-z0-9_-]{43}$/);
    return Response.json({
      access_token: jwt,
      refresh_token: "refresh",
      expires_in: 86400,
    });
  });
  vi.stubGlobal("fetch", fetch);
  const auth = await import("./auth");
  await auth.signIn();
  expect(await auth.currentSession()).toBe("session-one");
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fixture.reload).not.toHaveBeenCalled();
});
beforeEach(() => {
  vi.resetModules();
  fixture.records = {};
  fixture.launch.mockReset();
  fixture.reload.mockReset();
  vi.unstubAllGlobals();
});
test("serializes refresh rotation and refuses jobs bound to another sign-in session", async () => {
  fixture.records["know:identity"] = {
    accessToken: "expired",
    refreshToken: "refresh",
    expiresAt: 0,
    session: "session-one",
  };
  const fetch = vi.fn(async () => {
    await new Promise((resolve) => setTimeout(resolve, 10));
    return Response.json({
      access_token: jwt,
      refresh_token: "rotated",
      expires_in: 86400,
    });
  });
  vi.stubGlobal("fetch", fetch);
  const auth = await import("./auth");
  expect(
    await Promise.all([
      auth.accessToken("session-one"),
      auth.accessToken("session-one"),
    ]),
  ).toEqual([jwt, jwt]);
  expect(fetch).toHaveBeenCalledTimes(1);
  await expect(auth.accessToken("other-session")).rejects.toMatchObject({
    status: 401,
  });
  expect(fetch).toHaveBeenCalledTimes(1);
});
test("invalid refresh removes credentials and asks for explicit sign-in", async () => {
  fixture.records["know:identity"] = {
    accessToken: "expired",
    refreshToken: "used",
    expiresAt: 0,
    session: "session-one",
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      Response.json({ error: "invalid_grant" }, { status: 400 }),
    ),
  );
  const auth = await import("./auth");
  await expect(auth.accessToken("session-one")).rejects.toMatchObject({
    status: 401,
  });
  expect(await auth.currentSession()).toBeUndefined();
});
test("rejects a callback with mismatched state before exchanging the code", async () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  fixture.launch.mockResolvedValue(
    "https://fixture.extensions.allizom.org/?code=stolen&state=wrong",
  );
  const auth = await import("./auth");
  await expect(auth.signIn()).rejects.toThrow("could not be verified");
  expect(fetch).not.toHaveBeenCalled();
  expect(fixture.reload).not.toHaveBeenCalled();
});
test("unconfirmed logout preserves credentials so the user can retry", async () => {
  fixture.records["know:identity"] = {
    accessToken: jwt,
    refreshToken: "refresh",
    expiresAt: Date.now() + 86400_000,
    session: "session-one",
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(null, { status: 503 })),
  );
  const auth = await import("./auth");
  await expect(auth.signOut()).rejects.toThrow("not confirmed");
  expect(await auth.currentSession()).toBe("session-one");
  expect(fixture.reload).not.toHaveBeenCalled();
});
