import { beforeEach, expect, test, vi } from "vitest";
import { waitFor } from "@testing-library/react";

const fixture = vi.hoisted(() => ({
  records: {} as Record<string, unknown>,
  tabs: new Map<number, { id: number; url: string }>(),
  updates: [] as ((id: number, change: { url?: string }) => void)[],
  removals: [] as ((id: number) => void)[],
  alarms: [] as ((alarm: { name: string }) => void)[],
  launch: vi.fn(),
  changed: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
}));
vi.mock("wxt/browser", () => ({
  browser: {
    runtime: {
      id: "know@url-saver.local",
      getURL: (path: string) => "moz-extension://fixture" + path,
      getPlatformInfo: async () => ({ os: "android" }),
    },
    identity: undefined,
    storage: {
      local: {
        get: async (key: string) =>
          structuredClone({ [key]: fixture.records[key] }),
        set: async (values: Record<string, unknown>) => {
          Object.assign(fixture.records, structuredClone(values));
        },
        remove: async (key: string) => {
          delete fixture.records[key];
        },
      },
    },
    tabs: {
      create: fixture.create,
      update: fixture.update,
      remove: fixture.remove,
      get: async (id: number) => {
        const tab = fixture.tabs.get(id);
        if (!tab) throw new Error("Closed");
        return tab;
      },
      onUpdated: {
        addListener: (fn: (id: number, change: { url?: string }) => void) =>
          fixture.updates.push(fn),
      },
      onRemoved: {
        addListener: (fn: (id: number) => void) => fixture.removals.push(fn),
      },
    },
    alarms: {
      create: vi.fn(async () => {}),
      clear: vi.fn(async () => {}),
      onAlarm: {
        addListener: (fn: (alarm: { name: string }) => void) =>
          fixture.alarms.push(fn),
      },
    },
  },
}));
const pendingKey = "know:identity:pending-tab";
const jwt = "h." + btoa(JSON.stringify({ sid: "android-session" })) + ".s";
beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  fixture.records = {};
  fixture.tabs.clear();
  fixture.updates = [];
  fixture.removals = [];
  fixture.alarms = [];
  fixture.create.mockImplementation(async () => {
    const tab = { id: 7, url: "about:blank" };
    fixture.tabs.set(7, tab);
    return tab;
  });
  fixture.update.mockImplementation(
    async (id: number, values: { url?: string }) => {
      const tab = fixture.tabs.get(id);
      if (!tab) throw new Error("Closed");
      if (values.url) tab.url = values.url;
      return tab;
    },
  );
  fixture.remove.mockImplementation(async (id: number) => {
    fixture.tabs.delete(id);
    for (const fn of fixture.removals) fn(id);
  });
});
function emit(id: number, url: string) {
  const tab = fixture.tabs.get(id);
  if (tab) tab.url = url;
  for (const fn of fixture.updates) fn(id, { url });
}
async function start() {
  const auth = await import("./auth");
  auth.registerAuthentication(fixture.changed);
  await auth.signIn();
  const flow = fixture.records[pendingKey] as {
    redirect: string;
    state: string;
    verifier: string;
    tabId: number;
    expiresAt: number;
    phase: string;
  };
  return {
    auth,
    flow,
    callback: flow.redirect + "?code=once&state=" + flow.state,
  };
}
test("Android uses its own tab, exact stable callback and PKCE; other-tab events cannot steal or suppress completion", async () => {
  const fetch = vi.fn(async (_: unknown, init?: RequestInit) => {
    const params = new URLSearchParams(init?.body as URLSearchParams);
    expect(params.get("grant_type")).toBe("authorization_code");
    expect(params.get("code_verifier")).toMatch(/^[A-Za-z0-9_-]{43}$/);
    return Response.json({
      access_token: jwt,
      refresh_token: "rotating-refresh",
      expires_in: 86400,
    });
  });
  vi.stubGlobal("fetch", fetch);
  const { auth, flow, callback } = await start();
  expect(fixture.launch).not.toHaveBeenCalled();
  expect((await auth.authenticationStatus()).signingIn).toBe(true);
  expect(await auth.currentSession()).toBeUndefined();
  const url = new URL(fixture.tabs.get(7)!.url);
  expect(url.searchParams.get("redirect_uri")).toBe(flow.redirect);
  expect(url.searchParams.get("code_challenge_method")).toBe("S256");
  const digest = new Uint8Array(
    await crypto.subtle.digest(
      "SHA-256",
      new TextEncoder().encode(flow.verifier),
    ),
  );
  expect(url.searchParams.get("code_challenge")).toBe(
    btoa(String.fromCharCode(...digest))
      .replaceAll("+", "-")
      .replaceAll("/", "_")
      .replaceAll("=", ""),
  );
  emit(99, callback);
  emit(7, callback);
  emit(7, callback);
  await waitFor(() => expect(fixture.changed).toHaveBeenCalledTimes(1));
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(await auth.currentSession()).toBe("android-session");
  expect(fixture.records[pendingKey]).toBeUndefined();
  expect(fixture.remove).toHaveBeenCalledWith(7);
});
test("callback hash matches Firefox's documented implementation", async () => {
  const { firefoxRedirect } = await import("./tabAuth");
  expect(await firefoxRedirect("know@url-saver.local")).toMatch(
    /^https:\/\/[a-f0-9]{40}\.extensions\.allizom\.org\/$/,
  );
  expect(await firefoxRedirect("know@url-saver.local")).toBe(
    "https://d4fe62558fa735f936131a5aea25882bdbee9374.extensions.allizom.org/",
  );
});
test.each(["wrong-state", "duplicate-state", "duplicate-code"])(
  "rejects %s before token exchange",
  async (variant) => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    const { flow, callback } = await start();
    const invalid =
      variant === "wrong-state"
        ? flow.redirect + "?code=once&state=forged"
        : callback +
          (variant === "duplicate-state" ? "&state=forged" : "&code=other");
    emit(7, invalid);
    await waitFor(() => expect(fixture.records[pendingKey]).toBeUndefined());
    expect(fetch).not.toHaveBeenCalled();
    expect(fixture.records["know:identity"]).toBeUndefined();
  },
);
test("ignores a lookalike callback host and path", async () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  const { flow } = await start();
  emit(
    7,
    flow.redirect.replace(".org/", ".org.evil.test/") +
      "?code=once&state=" +
      flow.state,
  );
  emit(7, flow.redirect + "other?code=once&state=" + flow.state);
  for (let i = 0; i < 20; i++) await Promise.resolve();
  expect(fetch).not.toHaveBeenCalled();
  expect(fixture.records[pendingKey]).toBeTruthy();
});
test("resumes the callback after the background was unloaded without relying on an open popup", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      Response.json({
        access_token: jwt,
        refresh_token: "refresh",
        expires_in: 86400,
      }),
    ),
  );
  const { callback } = await start();
  fixture.tabs.get(7)!.url = callback;
  fixture.updates = [];
  fixture.removals = [];
  fixture.alarms = [];
  vi.resetModules();
  const auth = await import("./auth");
  auth.registerAuthentication(fixture.changed);
  await waitFor(() => expect(fixture.changed).toHaveBeenCalledTimes(1));
  expect(await auth.currentSession()).toBe("android-session");
});
test("cancelling or timing out clears the persisted verifier and allows another attempt", async () => {
  const { auth, flow } = await start();
  fixture.tabs.delete(7);
  for (const fn of fixture.removals) fn(7);
  await waitFor(() => expect(fixture.records[pendingKey]).toBeUndefined());
  await auth.signIn();
  fixture.records[pendingKey] = {
    ...(fixture.records[pendingKey] as object),
    expiresAt: Date.now() - 1,
  };
  const status = await auth.authenticationStatus();
  expect(status.signingIn).toBe(false);
  expect(status.message).toContain("expired");
  expect(fixture.records[pendingKey]).toBeUndefined();
  expect(flow.verifier).toBeTruthy();
});
test("an interrupted one-use exchange is not replayed on restart", async () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  await start();
  fixture.records[pendingKey] = {
    ...(fixture.records[pendingKey] as object),
    phase: "exchanging",
  };
  fixture.updates = [];
  fixture.removals = [];
  fixture.alarms = [];
  vi.resetModules();
  const auth = await import("./auth");
  auth.registerAuthentication(fixture.changed);
  await waitFor(() => expect(fixture.records[pendingKey]).toBeUndefined());
  expect(fetch).not.toHaveBeenCalled();
  expect((await auth.authenticationStatus()).message).toContain("interrupted");
});
