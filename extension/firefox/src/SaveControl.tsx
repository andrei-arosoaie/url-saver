import { useEffect, useState } from "react";
import type { Capture } from "./capture";
import styles from "./SaveControl.module.css";
export type Reply = {
  ok: boolean;
  data?: { article?: unknown; version?: number };
  status?: number;
  reason?: string;
  id?: string;
  message?: string;
};
export type Send = (
  action: "save" | "existing" | "update",
  capture: Capture,
  id?: string,
  version?: number,
) => Promise<Reply>;
export function SaveControl({
  extract,
  send,
}: {
  extract: () => Capture;
  send: Send;
}) {
  const [state, setState] = useState("ready");
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState<Capture>();
  const [duplicate, setDuplicate] = useState<{ id: string; version: number }>();
  const busy = state === "saving";
  useEffect(() => {
    if (state !== "success") return;
    const timer = setTimeout(() => setState("saved"), 300);
    return () => clearTimeout(timer);
  }, [state]);
  function fail(text: string) {
    setState("error");
    setMessage(text);
    setPending(undefined);
    setDuplicate(undefined);
  }
  async function save(value: Capture) {
    setState("saving");
    setMessage("Saving article…");
    try {
      const result = await send("save", value);
      if (result.ok) {
        setState("success");
        setMessage(value.content ? "Article saved." : "Link saved.");
        setPending(undefined);
        setDuplicate(undefined);
        return;
      }
      if (
        result.status === 409 &&
        result.reason === "DUPLICATE_URL" &&
        result.id
      ) {
        const existing = await send("existing", value, result.id);
        if (!existing.ok || !Number.isSafeInteger(existing.data?.version))
          throw new Error(
            existing.message || "Unable to load the existing article.",
          );
        setPending(value);
        setDuplicate({ id: result.id, version: existing.data!.version! });
        setState("duplicate");
        setMessage("This URL is already saved. Update its saved content?");
        return;
      }
      fail(result.message || "Save not confirmed. Click Save to retry.");
    } catch (error) {
      fail(
        error instanceof Error
          ? error.message
          : "Save not confirmed. Click Save to retry.",
      );
    }
  }
  async function click() {
    if (busy || state === "duplicate" || state === "fallback") return;
    try {
      const value = extract();
      if (!value.content) {
        setPending(value);
        setState("fallback");
        setMessage("No readable article found. Save URL and title only?");
      } else await save(value);
    } catch {
      fail("Unable to read this page. Click Save to retry.");
    }
  }
  async function update() {
    if (!duplicate || !pending || busy) return;
    setState("saving");
    setMessage("Updating article…");
    try {
      const result = await send(
        "update",
        pending,
        duplicate.id,
        duplicate.version,
      );
      if (!result.ok) throw new Error(result.message);
      setPending(undefined);
      setDuplicate(undefined);
      setState("success");
      setMessage("Article updated.");
    } catch (error) {
      fail(error instanceof Error ? error.message : "Update not confirmed.");
    }
  }
  function cancel() {
    setPending(undefined);
    setDuplicate(undefined);
    setState("ready");
    setMessage("");
  }
  return (
    <div className={styles.root}>
      <button
        className={`${styles.circle} ${styles[state] || ""}`}
        aria-label="Know: Save article"
        aria-busy={busy}
        disabled={busy}
        onClick={() => void click()}
      >
        <span aria-hidden="true">
          {busy ? (
            "…"
          ) : state === "error" ? (
            "!"
          ) : state === "success" || state === "saved" ? (
            "✓"
          ) : (
            <svg
              width="22"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
            >
              <path d="M6 3h12v18l-6-4-6 4z" />
            </svg>
          )}
        </span>
      </button>
      {state !== "ready" && (
        <section className={styles.panel} aria-label="Know extension feedback">
          <header>
            KNOW <small>Firefox extension</small>
            <button
              aria-label="Dismiss Know message"
              disabled={busy}
              onClick={cancel}
            >
              ×
            </button>
          </header>
          <p role={state === "error" ? "alert" : "status"}>{message}</p>
          {state === "duplicate" && (
            <div>
              <button onClick={() => void update()}>Update existing</button>
              <button onClick={cancel}>Cancel</button>
            </div>
          )}
          {state === "fallback" && (
            <div>
              <button onClick={() => pending && void save(pending)}>
                Save link only
              </button>
              <button onClick={cancel}>Cancel</button>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
