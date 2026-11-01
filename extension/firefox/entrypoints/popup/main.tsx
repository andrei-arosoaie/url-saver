import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { browser } from "wxt/browser";
import "./style.css";
function Account() {
  const [signedIn, setSignedIn] = useState<boolean | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    void browser.runtime
      .sendMessage({ type: "know:identity", action: "status" })
      .then((result) => setSignedIn(!!result?.signedIn));
  }, []);
  async function act() {
    setBusy(true);
    setMessage("");
    try {
      const result = await browser.runtime.sendMessage({
        type: "know:identity",
        action: signedIn ? "signOut" : "signIn",
      });
      if (!result?.ok)
        throw new Error(
          result?.message ||
            "Account action was not confirmed. Reopen Know to check.",
        );
      setSignedIn(!signedIn);
      setMessage(
        signedIn
          ? "Signed out."
          : "Signed in. Return to your article and click Save.",
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
        {signedIn === null
          ? "Connecting…"
          : signedIn
            ? "You are signed in."
            : "Sign in to save articles to your library."}
      </p>
      <button disabled={busy || signedIn === null} onClick={() => void act()}>
        {busy ? "Please wait…" : signedIn ? "Sign out" : "Sign in with Google"}
      </button>
      {message && <p role="status">{message}</p>}
    </main>
  );
}
createRoot(document.getElementById("root")!).render(<Account />);
