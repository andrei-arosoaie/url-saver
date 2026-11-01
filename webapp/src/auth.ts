const nativeFetch = globalThis.fetch.bind(globalThis);
let accessToken: string | undefined;
let expiresAt = 0;
let refreshing: Promise<boolean> | undefined;
let boundSession: string | undefined;
let ended = false;
let generation = 0;
function endSession() {
  accessToken = undefined;
  expiresAt = 0;
  ended = true;
  generation++;
  globalThis.dispatchEvent(new Event("know-signed-out"));
}
const channel =
  typeof BroadcastChannel === "function"
    ? new BroadcastChannel("know-auth")
    : undefined;
channel?.addEventListener("message", () => {
  endSession();
});

function random() {
  return base64(crypto.getRandomValues(new Uint8Array(32)));
}
function base64(bytes: Uint8Array) {
  return btoa(String.fromCharCode(...bytes))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}
async function csrf(): Promise<string> {
  const response = await nativeFetch("/oauth2/csrf", {
    signal: AbortSignal.timeout(60_000),
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!response.ok)
    throw new Error("Could not connect to sign-in. Please try again.");
  const data = await response.json();
  if (typeof data.csrf_token !== "string")
    throw new Error("Invalid sign-in response.");
  return data.csrf_token;
}
async function token(params: Record<string, string>) {
  const tokenGeneration = generation;
  const response = await nativeFetch("/oauth2/token", {
    signal: AbortSignal.timeout(60_000),
    method: "POST",
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "X-Know-CSRF": await csrf(),
    },
    body: new URLSearchParams({
      client_id: "know-web",
      ...(boundSession ? { expected_session: boundSession } : {}),
      ...params,
    }),
  });
  if (!response.ok) {
    if (response.status >= 500)
      throw new Error("Sign-in service is unavailable. Please try again.");
    endSession();
    return false;
  }
  const result = await response.json();
  if (tokenGeneration !== generation) return false;
  if (
    typeof result.access_token !== "string" ||
    typeof result.expires_in !== "number"
  )
    throw new Error("Invalid sign-in response.");
  const payload = JSON.parse(
    atob(
      result.access_token
        .split(".")[1]
        .replaceAll("-", "+")
        .replaceAll("_", "/"),
    ),
  );
  if (typeof payload.sid !== "string")
    throw new Error("Invalid sign-in session.");
  if (boundSession && boundSession !== payload.sid) {
    endSession();
    return false;
  }
  boundSession = payload.sid;
  ended = false;
  accessToken = result.access_token;
  expiresAt = Date.now() + result.expires_in * 1000;
  if (params.grant_type === "authorization_code")
    channel?.postMessage("signed-in");
  return true;
}
async function coordinatedRefresh() {
  const work = () => token({ grant_type: "refresh_token" });
  // Tabs share the HttpOnly refresh cookie; serialize its rotation across tabs.
  if (navigator.locks) return navigator.locks.request("know-refresh", work);
  return work();
}
export async function refreshAuthentication() {
  if (!refreshing)
    refreshing = coordinatedRefresh().finally(() => {
      refreshing = undefined;
    });
  return refreshing;
}
export async function beginSignIn() {
  const verifier = random(),
    state = random();
  const challenge = base64(
    new Uint8Array(
      await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)),
    ),
  );
  sessionStorage.setItem(
    "know-pkce",
    JSON.stringify({ verifier, state, createdAt: Date.now() }),
  );
  const params = new URLSearchParams({
    response_type: "code",
    client_id: "know-web",
    redirect_uri: `${location.origin}/auth/callback`,
    code_challenge: challenge,
    code_challenge_method: "S256",
    state,
  });
  location.assign(`/oauth2/authorize?${params}`);
}
export async function initializeAuthentication() {
  if (location.pathname === "/auth/callback") {
    const raw = sessionStorage.getItem("know-pkce");
    sessionStorage.removeItem("know-pkce");
    const params = new URLSearchParams(location.search);
    history.replaceState(null, "", "/");
    if (!raw) throw new Error("Sign-in expired. Please sign in again.");
    const pending = JSON.parse(raw);
    if (
      params.get("state") !== pending.state ||
      Date.now() - pending.createdAt > 300_000 ||
      !params.get("code")
    )
      throw new Error("Sign-in could not be verified. Please try again.");
    return token({
      grant_type: "authorization_code",
      code: params.get("code")!,
      code_verifier: pending.verifier,
      redirect_uri: `${location.origin}/auth/callback`,
    });
  }
  if (new URLSearchParams(location.search).has("auth_error")) {
    history.replaceState(null, "", "/");
    throw new Error(
      "Google sign-in was cancelled or failed. Please try again.",
    );
  }
  return refreshAuthentication();
}
export async function signOut() {
  const response = await nativeFetch("/oauth2/logout", {
    signal: AbortSignal.timeout(60_000),
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "X-Know-CSRF": await csrf(),
    },
    body: new URLSearchParams({
      client_id: "know-web",
      ...(boundSession ? { expected_session: boundSession } : {}),
    }),
  });
  if (!response.ok)
    throw new Error("Sign-out was not confirmed. Please try again.");
  endSession();
  channel?.postMessage("signed-out");
}
export function installAuthenticatedFetch() {
  globalThis.fetch = async (input, init) => {
    const original = new Request(
      input instanceof Request ? input : new URL(input, location.href),
      init,
    );
    const url = new URL(original.url);
    if (url.origin !== location.origin || !url.pathname.startsWith("/api/"))
      return nativeFetch(input, init);
    const requestGeneration = generation;
    if (ended)
      throw new Error("Your sign-in changed. Sign in again before continuing.");
    if (!accessToken || Date.now() >= expiresAt - 30_000)
      await refreshAuthentication();
    if (ended || requestGeneration !== generation)
      throw new Error("Your sign-in changed. Sign in again before continuing.");
    const send = () => {
      const request = original.clone();
      if (accessToken)
        request.headers.set("Authorization", `Bearer ${accessToken}`);
      return nativeFetch(request);
    };
    let response = await send();
    if (response.status === 401) {
      if (await refreshAuthentication()) {
        if (requestGeneration !== generation || ended)
          throw new Error(
            "Your sign-in changed. Sign in again before continuing.",
          );
        response = await send();
      }
      if (response.status === 401)
        globalThis.dispatchEvent(new Event("know-signed-out"));
    }
    return response;
  };
}
