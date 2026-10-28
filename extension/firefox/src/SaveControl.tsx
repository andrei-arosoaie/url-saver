import { useEffect, useState, useRef } from "react";
import type { SaveReply } from "./saveQueue";
import { maxRetries } from "./retrySave";
import type { Capture } from "./capture";
import styles from "./SaveControl.module.css";
export type Reply = SaveReply & { idle?: boolean };
export type Send = (
  action: "save" | "existing" | "update" | "status" | "acknowledge",
  capture: Capture,
  id?: string,
  version?: number,
) => Promise<Reply>;
export function SaveControl({
  extract,
  send,
  pageUrl,
}: {
  extract: () => Capture;
  send: Send;
  pageUrl?: string;
}) {
  const [state, setState] = useState("ready");
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState<Capture>();
  const [duplicate, setDuplicate] = useState<{ id: string; version: number }>();
  const [progress, setProgress] = useState<Reply["progress"]>();
  const [clock, setClock] = useState(Date.now());
  const alive = useRef(true);
  const inFlight = useRef(false);
  const interaction = useRef(0);
  const terminalJob = useRef<string | undefined>(undefined);
  const busy = state === "saving" || state === "retrying";
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    if (!pageUrl) return;
    let stopped = false;
    let querying = false;
    const poll = async () => {
      if (querying || inFlight.current) return;
      querying = true;
      const revision = interaction.current;
      try {
        const result = await send("status", { url: pageUrl, title: "" });
        if (
          !stopped &&
          revision === interaction.current &&
          !inFlight.current &&
          !result.idle
        )
          await receive(result, result.capture);
      } catch {
        if (!stopped && revision === interaction.current && !inFlight.current)
          fail(
            "Could not read pending save status. Your save is not confirmed. Reopen this page to check again.",
          );
      } finally {
        querying = false;
      }
    };
    void poll();
    const timer = setInterval(() => {
      setClock(Date.now());
      void poll();
    }, 1000);
    return () => {
      stopped = true;
      clearInterval(timer);
    };
  }, [pageUrl, send]);
  useEffect(() => {
    if (state !== "success") return;
    const timer = setTimeout(() => setState("saved"), 300);
    return () => clearTimeout(timer);
  }, [state]);
  function fail(text: string) {
    if (!alive.current) return;
    setState("error");
    setMessage(text);
    setPending(undefined);
    setDuplicate(undefined);
    setProgress(undefined);
  }
  async function receive(result: Reply, value?: Capture) {
    if (!alive.current) return;
    if (result.progress) {
      setProgress(result.progress);
      setState("retrying");
      return;
    }
    setProgress(undefined);
    if (result.jobId && terminalJob.current === result.jobId) return;
    terminalJob.current = result.jobId;
    if (result.ok) {
      setState("success");
      setMessage(
        result.message ||
          (value && !value.content ? "Link saved." : "Article saved."),
      );
      setPending(undefined);
      setDuplicate(undefined);
      if (pageUrl)
        await send("acknowledge", { url: pageUrl, title: "" }).catch(() => {});
      return;
    }
    if (
      result.status === 409 &&
      result.reason === "DUPLICATE_URL" &&
      result.id &&
      value
    ) {
      inFlight.current = true;
      try {
        const existing = await send("existing", value, result.id);
        if (!alive.current) return;
        if (!existing.ok || !Number.isSafeInteger(existing.data?.version))
          throw new Error(
            existing.message || "Unable to load the existing article.",
          );
        setPending(value);
        setDuplicate({ id: result.id, version: existing.data!.version! });
        setState("duplicate");
        setMessage("This URL is already saved. Update its saved content?");
      } finally {
        inFlight.current = false;
      }
      return;
    }
    fail(result.message || "Save not confirmed. Click Save to retry.");
  }
  async function save(value: Capture) {
    if (inFlight.current) return;
    setState("saving");
    setMessage("Saving article…");
    inFlight.current = true;
    try {
      const result = await send("save", value);
      await receive(result, result.capture ?? value);
    } catch (error) {
      fail(
        error instanceof Error
          ? error.message
          : "Save not confirmed. Click Save to retry.",
      );
    } finally {
      inFlight.current = false;
    }
  }
  async function click() {
    if (
      inFlight.current ||
      busy ||
      state === "duplicate" ||
      state === "fallback"
    )
      return;
    interaction.current++;
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
    if (!duplicate || !pending || busy || inFlight.current) return;
    terminalJob.current = undefined;
    interaction.current++;
    setState("saving");
    setMessage("Updating article…");
    inFlight.current = true;
    try {
      const result = await send(
        "update",
        pending,
        duplicate.id,
        duplicate.version,
      );
      if (result.progress) {
        await receive(result);
        return;
      }
      if (!result.ok) throw new Error(result.message);
      setPending(undefined);
      setDuplicate(undefined);
      setState("success");
      setMessage("Article updated.");
      if (pageUrl)
        await send("acknowledge", { url: pageUrl, title: "" }).catch(() => {});
    } catch (error) {
      fail(error instanceof Error ? error.message : "Update not confirmed.");
    } finally {
      inFlight.current = false;
    }
  }
  function cancel() {
    interaction.current++;
    if (pageUrl)
      void send("acknowledge", { url: pageUrl, title: "" }).catch(() => {});
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
          <p role={state === "error" ? "alert" : "status"}>
            {progress
              ? progress.target === "screenshot"
                ? progress.phase === "waiting" && progress.retry > 0
                  ? `Article saved. Screenshot upload will retry in ${Math.max(0, Math.ceil(((progress.nextAttemptAt ?? clock) - clock) / 1000))} seconds (${progress.retry} of ${maxRetries}).`
                  : "Article saved. Uploading screenshot..."
                : progress.phase === "waiting"
                  ? `Server did not respond. Your save is not confirmed. Retrying in ${Math.max(0, Math.ceil(((progress.nextAttemptAt ?? clock) - clock) / 1000))} seconds · retry ${progress.retry} of ${maxRetries}.`
                  : progress.retry
                    ? `Retrying save · ${progress.retry} of ${maxRetries}.`
                    : "Saving article…"
              : message}
          </p>
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
