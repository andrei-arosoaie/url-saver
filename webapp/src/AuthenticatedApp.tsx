import { useEffect, useState } from "react";
import { App } from "./App";
import { beginSignIn, initializeAuthentication, signOut } from "./auth";

let initialization: Promise<boolean> | undefined;
export function AuthenticatedApp() {
  const [signedIn, setSignedIn] = useState<boolean | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    initialization ??= initializeAuthentication();
    initialization
      .then((result) => {
        if (active) setSignedIn(result);
      })
      .catch((reason: unknown) => {
        if (active) {
          setSignedIn(false);
          setError(
            reason instanceof Error
              ? reason.message
              : "Could not connect. Please try again.",
          );
        }
      });
    const lost = () => {
      setSignedIn(false);
      initialization = undefined;
    };
    window.addEventListener("know-signed-out", lost);
    return () => {
      active = false;
      window.removeEventListener("know-signed-out", lost);
    };
  }, []);
  if (signedIn === null)
    return (
      <main className="auth-screen">
        <p>Connecting to Know…</p>
      </main>
    );
  if (!signedIn)
    return (
      <main className="auth-screen">
        <h1>Know</h1>
        <p>Your articles, connections, and things to explore.</p>
        {error && <p role="alert">{error}</p>}
        <button
          onClick={() => {
            void beginSignIn().catch(() =>
              setError("Could not start sign-in. Please try again."),
            );
          }}
        >
          Sign in with Google
        </button>
        <p className="auth-version">
          v{__APP_BUILD__.version} · {__APP_BUILD__.revision}
        </p>
      </main>
    );
  return (
    <>
      <div className="auth-account">
        <button
          onClick={() => {
            void signOut()
              .then(() => {
                initialization = undefined;
                setSignedIn(false);
              })
              .catch((reason: Error) => setError(reason.message));
          }}
        >
          Sign out
        </button>
        {error && <span role="alert">{error}</span>}
      </div>
      <App />
    </>
  );
}
