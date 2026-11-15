import { browser } from "wxt/browser";

const pendingKey = "know:identity:pending-tab";
const feedbackKey = "know:identity:feedback";
const alarmName = "know:identity:sign-in-expiry";
const lifetime = 5 * 60_000;
type Pending = {
  redirect: string;
  state: string;
  verifier: string;
  previousSession?: string;
  tabId?: number;
  expiresAt: number;
  phase: "opening" | "waiting" | "exchanging";
};
type Handlers = {
  exchange: (
    code: string,
    verifier: string,
    redirect: string,
    current: () => Promise<boolean>,
  ) => Promise<void>;
  session: () => Promise<string | undefined>;
  changed: () => void;
};
let completing: Promise<void> | undefined;
let handlers: Handlers | undefined;
function serialize(operation: () => Promise<void>) {
  completing = (completing ?? Promise.resolve())
    .catch(() => {})
    .then(operation);
  return completing;
}
async function pending(): Promise<Pending | undefined> {
  return (await browser.storage.local.get(pendingKey))[pendingKey] as
    Pending | undefined;
}
async function finish(message?: string, close = true) {
  const flow = await pending();
  await browser.storage.local.remove(pendingKey);
  await browser.alarms.clear(alarmName);
  if (message)
    await browser.storage.local.set({
      [feedbackKey]: { message, expiresAt: Date.now() + lifetime },
    });
  else await browser.storage.local.remove(feedbackKey);
  if (close && flow?.tabId !== undefined)
    await browser.tabs.remove(flow.tabId).catch(() => {});
}
export async function tabSignInStatus() {
  let flow = await pending();
  if (flow && flow.expiresAt <= Date.now()) {
    await serialize(async () => {
      const active = await pending();
      if (active && active.expiresAt <= Date.now())
        await finish("Sign-in expired. Please try again.");
    });
    flow = await pending();
  }
  const feedback = (await browser.storage.local.get(feedbackKey))[
    feedbackKey
  ] as { message: string; expiresAt: number } | undefined;
  return {
    signingIn: !!flow && flow.expiresAt > Date.now(),
    message:
      feedback && feedback.expiresAt > Date.now()
        ? feedback.message
        : undefined,
  };
}

// Firefox derives this public callback from the stable add-on ID with SHA-1.
// It is an identifier, not a security hash. State and S256 PKCE protect the flow.
export async function firefoxRedirect(id: string) {
  const digest = new Uint8Array(
    await crypto.subtle.digest("SHA-1", new TextEncoder().encode(id)),
  );
  const hash = Array.from(digest, (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
  return `https://${hash}.extensions.allizom.org/`;
}
export function startTabSignIn(
  url: string,
  flow: Pick<Pending, "redirect" | "state" | "verifier" | "previousSession">,
) {
  return serialize(() => start(url, flow));
}
async function start(
  url: string,
  flow: Pick<Pending, "redirect" | "state" | "verifier" | "previousSession">,
) {
  const existing = await pending();
  if (existing?.phase === "exchanging" && existing.expiresAt > Date.now())
    return;
  if (
    existing &&
    existing.expiresAt > Date.now() &&
    existing.tabId !== undefined
  ) {
    try {
      await browser.tabs.update(existing.tabId, { active: true });
      return;
    } catch {
      await finish(undefined, false);
    }
  }
  await finish();
  const record: Pending = {
    ...flow,
    phase: "opening",
    expiresAt: Date.now() + lifetime,
  };
  await browser.storage.local.set({ [pendingKey]: record });
  try {
    // Bind the tab before navigating: even an immediate callback must match its ID.
    const tab = await browser.tabs.create({ url: "about:blank", active: true });
    if (tab.id === undefined)
      throw new Error("Could not open the sign-in tab.");
    record.tabId = tab.id;
    record.phase = "waiting";
    await browser.storage.local.set({ [pendingKey]: record });
    await browser.alarms.create(alarmName, { when: record.expiresAt });
    await browser.tabs.update(tab.id, { url });
  } catch (error) {
    await finish("Could not open sign-in. Please try again.");
    throw error;
  }
}
async function callback(tabId: number, value: string) {
  const flow = await pending();
  if (!flow || flow.tabId !== tabId || flow.phase !== "waiting") return;
  if (flow.expiresAt <= Date.now()) {
    await finish("Sign-in expired. Please try again.");
    return;
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return;
  }
  if (url.origin + url.pathname !== flow.redirect) return;
  try {
    if (
      url.searchParams.getAll("state").length !== 1 ||
      url.searchParams.get("state") !== flow.state ||
      url.searchParams.getAll("code").length !== 1 ||
      !url.searchParams.get("code") ||
      url.searchParams.has("error") ||
      url.hash
    ) {
      throw new Error("Sign-in could not be verified. Please try again.");
    }
    if (!handlers) throw new Error("Sign-in is not ready. Please try again.");
    // A restarted background must never redeem an uncertain one-use code twice.
    flow.phase = "exchanging";
    flow.expiresAt = Date.now() + 65_000;
    await browser.storage.local.set({ [pendingKey]: flow });
    await browser.alarms.create(alarmName, { when: flow.expiresAt });
    // The Firefox callback is a synthetic endpoint, not a web page. Replace it
    // immediately with a bundled waiting page while the background redeems PKCE.
    await browser.tabs.update(tabId, {
      url: browser.runtime.getURL("/sign-in.html"),
    });
    await handlers.exchange(
      url.searchParams.get("code")!,
      flow.verifier,
      flow.redirect,
      async () => {
        const active = await pending();
        return (
          active?.state === flow.state &&
          active.phase === "exchanging" &&
          active.expiresAt > Date.now()
        );
      },
    );
    await finish();
    handlers.changed();
  } catch {
    await finish("Sign-in could not be completed. Please try again.");
  }
}
function handleCallback(tabId: number, url: string) {
  return serialize(() => callback(tabId, url));
}
async function resume() {
  const flow = await pending();
  if (!flow) return;
  if (flow.phase === "exchanging") {
    const session = await handlers?.session();
    if (session && session !== flow.previousSession) {
      await finish();
      handlers?.changed();
    } else await finish("Sign-in was interrupted. Please try again.");
    return;
  }
  if (flow.expiresAt <= Date.now() || flow.tabId === undefined) {
    await finish("Sign-in expired. Please try again.");
    return;
  }
  try {
    const tab = await browser.tabs.get(flow.tabId);
    await browser.alarms.create(alarmName, { when: flow.expiresAt });
    if (tab.url) await callback(flow.tabId, tab.url);
  } catch {
    await finish("Sign-in was cancelled. Please try again.", false);
  }
}

// Called synchronously from the background entry point: these listeners wake an
// unloaded event page on Android; no popup or content script needs to stay open.
export function registerTabSignIn(next: Handlers) {
  handlers = next;
  browser.tabs.onUpdated.addListener((id, change) => {
    if (change.url) void handleCallback(id, change.url).catch(() => {});
  });
  browser.tabs.onRemoved.addListener((id) => {
    void serialize(async () => {
      const flow = await pending();
      if (flow?.tabId === id && flow.phase !== "exchanging")
        await finish("Sign-in was cancelled. Please try again.", false);
    }).catch(() => {});
  });
  browser.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === alarmName) void tabSignInStatus().catch(() => {});
  });
  void serialize(resume).catch(() => {});
}
