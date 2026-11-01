import { browser } from "wxt/browser";
import { ApiFailure } from "./api";

export const apiOrigin =
  import.meta.env.VITE_API_ORIGIN || "https://url-saver-server.onrender.com";
type Credentials = {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  session: string;
};
const storageKey = "know:identity";
let refreshing: Promise<Credentials | undefined> | undefined;
let signingIn: Promise<void> | undefined;
let signingOut = false;
function base64(bytes: Uint8Array) {
  return btoa(String.fromCharCode(...bytes))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}
function random() {
  return base64(crypto.getRandomValues(new Uint8Array(32)));
}
async function stored(): Promise<Credentials | undefined> {
  return (await browser.storage.local.get(storageKey))[storageKey] as
    Credentials | undefined;
}
export async function currentSession() {
  return (await stored())?.session;
}
async function exchange(
  params: Record<string, string>,
): Promise<Credentials | undefined> {
  const response = await fetch(new URL("/oauth2/token", apiOrigin), {
    method: "POST",
    credentials: "omit",
    redirect: "error",
    signal: AbortSignal.timeout(60_000),
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: "know-firefox", ...params }),
  });
  if (!response.ok) {
    if (response.status === 400 || response.status === 401)
      await browser.storage.local.remove(storageKey);
    else throw new Error("Sign-in service is unavailable. Please try again.");
    return;
  }
  const result = await response.json();
  if (
    typeof result.access_token !== "string" ||
    typeof result.refresh_token !== "string" ||
    typeof result.expires_in !== "number"
  )
    throw new Error("Invalid sign-in response.");
  // This claim is used only to bind local jobs, never to authorize an API request.
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
  const credentials = {
    accessToken: result.access_token,
    refreshToken: result.refresh_token,
    expiresAt: Date.now() + result.expires_in * 1000,
    session: payload.sid,
  };
  await browser.storage.local.set({ [storageKey]: credentials });
  return credentials;
}
export async function accessToken(expectedSession?: string) {
  if (signingIn || signingOut)
    throw new ApiFailure(
      401,
      undefined,
      undefined,
      "Finish signing in or out, then click Save again.",
    );
  let credentials = await stored();
  if (
    !credentials ||
    !expectedSession ||
    credentials.session !== expectedSession
  )
    throw new ApiFailure(
      401,
      undefined,
      undefined,
      "Sign in to Know, then click Save again.",
    );
  if (credentials.expiresAt <= Date.now() + 30_000) {
    refreshing ??= exchange({
      grant_type: "refresh_token",
      refresh_token: credentials.refreshToken,
    }).finally(() => {
      refreshing = undefined;
    });
    credentials = await refreshing;
  }
  if (!credentials || credentials.session !== expectedSession)
    throw new ApiFailure(
      401,
      undefined,
      undefined,
      "Sign in to Know, then click Save again.",
    );
  return credentials.accessToken;
}
export async function signIn() {
  if (signingOut) throw new Error("Finish signing out before signing in.");
  if (signingIn) return signingIn;
  signingIn = (async () => {
    if (refreshing) await refreshing;
    const verifier = random(),
      state = random(),
      redirect = browser.identity.getRedirectURL();
    const challenge = base64(
      new Uint8Array(
        await crypto.subtle.digest(
          "SHA-256",
          new TextEncoder().encode(verifier),
        ),
      ),
    );
    const url = new URL("/oauth2/authorize", apiOrigin);
    url.search = new URLSearchParams({
      response_type: "code",
      client_id: "know-firefox",
      redirect_uri: redirect,
      code_challenge: challenge,
      code_challenge_method: "S256",
      state,
    }).toString();
    const callback = await browser.identity.launchWebAuthFlow({
      url: url.href,
      interactive: true,
    });
    if (!callback) throw new Error("Sign-in was cancelled.");
    const result = new URL(callback);
    if (
      result.origin + result.pathname !== redirect ||
      result.searchParams.get("state") !== state ||
      !result.searchParams.get("code")
    )
      throw new Error("Sign-in could not be verified.");
    const credentials = await exchange({
      grant_type: "authorization_code",
      code: result.searchParams.get("code")!,
      code_verifier: verifier,
      redirect_uri: redirect,
    });
    if (!credentials) throw new Error("Sign-in expired. Please try again.");
    // The background replaces its queue workers after sign-in. Signing in never saves.
  })().finally(() => {
    signingIn = undefined;
  });
  return signingIn;
}
export async function signOut() {
  if (signingIn || signingOut)
    throw new Error("An account action is already in progress. Please wait.");
  signingOut = true;
  try {
    if (refreshing) await refreshing;
    const credentials = await stored();
    if (credentials) {
      const response = await fetch(new URL("/oauth2/logout", apiOrigin), {
        method: "POST",
        credentials: "omit",
        redirect: "error",
        signal: AbortSignal.timeout(60_000),
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          client_id: "know-firefox",
          refresh_token: credentials.refreshToken,
        }),
      });
      if (!response.ok)
        throw new Error("Sign-out was not confirmed. Please try again.");
    }
    await browser.storage.local.remove(storageKey);
  } finally {
    signingOut = false;
  }
}
