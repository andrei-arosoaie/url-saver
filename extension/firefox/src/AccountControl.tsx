import { useCallback, useEffect, useState } from "react";
import { browser } from "wxt/browser";
export function AccountControl() {
  const [signedIn, setSignedIn] = useState<boolean | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState(false);
  const [statusError, setStatusError] = useState(false);
  const refresh = useCallback(async () => {
    try {
      const result = await browser.runtime.sendMessage({
        type: "know:identity",
        action: "status",
      });
      if (typeof result?.signedIn !== "boolean") throw new Error("Unavailable");
      setSignedIn(result.signedIn);
      setPending(!!result.signingIn);
      setStatusError(false);
      setMessage(result.message || "");
    } catch {
      setStatusError(true);
      setMessage("Could not connect to Know. Retry to check your account.");
    }
  }, []);
  useEffect(() => {
    void refresh();
    if (!pending) return;
    const timer = setInterval(() => void refresh(), 1000);
    return () => clearInterval(timer);
  }, [refresh, pending]);
  async function act() {
    setBusy(true);
    setMessage("");
    try {
      if (statusError) {
        await refresh();
        return;
      }
      const result = await browser.runtime.sendMessage({
        type: "know:identity",
        action: signedIn && !pending ? "signOut" : "signIn",
      });
      if (!result?.ok)
        throw new Error(
          result?.message ||
            "Account action was not confirmed. Reopen Know to check.",
        );
      setSignedIn(!!result.signedIn);
      setPending(!!result.signingIn);
      setMessage(
        result.signingIn
          ? "Finish signing in in the new Firefox tab, then return to your article and click Save."
          : result.signedIn
            ? "Signed in. Return to your article and click Save."
            : "Signed out.",
      );
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Please try again.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <main>
      <h1>Know</h1>
      <p>
        Saving sends the page URL, article text and a screenshot of the visible
        tab to Know.
      </p>
      <p>
        {pending
          ? "Sign-in is in progress in another Firefox tab."
          : signedIn === null
            ? "Connecting…"
            : signedIn
              ? "You are signed in."
              : "Sign in to save articles to your library."}
      </p>
      <button
        disabled={busy || (signedIn === null && !statusError)}
        onClick={() => void act()}
      >
        {busy
          ? "Please wait…"
          : statusError
            ? "Retry connection"
            : pending
              ? "Return to sign-in"
              : signedIn
                ? "Sign out"
                : "Sign in with Google"}
      </button>
      {message && <p role="status">{message}</p>}
    </main>
  );
}
