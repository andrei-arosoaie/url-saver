import { useEffect, useRef, useState } from "react";
import type { Article } from "../api";
import type { Identification, IdentificationApi } from "./api";
import styles from "./IdentificationPanel.module.css";

const active = (value: Identification) =>
  value.status === "PENDING" || value.status === "RUNNING";
const source = (value: string) =>
  value === "USER" ? "Your choice" : value === "AI" ? "AI" : "Not identified";
function message(value: Identification) {
  if (
    value.categorySource === "USER" &&
    value.nameSource === "USER" &&
    !active(value)
  )
    return "Your choices are preserved. No AI needed.";
  if (value.status === "PENDING") return "Waiting to identify…";
  if (value.status === "RUNNING") return "Identifying missing details…";
  if (value.status === "SUCCEEDED") return "Identification complete";
  if (value.reason === "NO_ARTICLE_TEXT")
    return "No article text available for identification.";
  if (value.reason === "NO_MISSING_FIELDS")
    return "Your choices are preserved. No AI needed.";
  if (value.reason === "ARTICLE_CHANGED")
    return "Article text changed. Request identification again.";
  if (value.reason === "INSUFFICIENT_EVIDENCE")
    return "Not enough information to identify this article.";
  if (value.status === "FAILED")
    return "Identification failed. Both providers were unable to complete it; a provider may be in cooldown. Your article is saved.";
  return "Identify missing details from the saved article text.";
}
export function IdentificationPanel({
  article,
  api,
  onFinished,
}: {
  article: Article;
  api: IdentificationApi;
  onFinished: (signal: AbortSignal) => Promise<void>;
}) {
  const [value, setValue] = useState<Identification | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [revision, setRevision] = useState(0);
  const callback = useRef(onFinished);
  const completed = useRef("");
  const mutation = useRef<AbortController | null>(null);
  useEffect(() => {
    callback.current = onFinished;
  }, [onFinished]);
  useEffect(() => () => mutation.current?.abort(), []);
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let running = false;
    async function refresh() {
      if (running || controller.signal.aborted) return;
      running = true;
      try {
        const next = await api.status(article.id, controller.signal);
        if (controller.signal.aborted) return;
        setValue(next);
        setError("");
        setUncertain(false);
        if (next.completedAt) {
          const signature = `${next.status}:${next.requestedAt}:${next.completedAt}`;
          if (signature !== completed.current) {
            completed.current = signature;
            try {
              await callback.current(controller.signal);
            } catch (cause) {
              if (!controller.signal.aborted && completed.current === signature)
                completed.current = "";
              throw cause;
            }
          }
        }
        if (!controller.signal.aborted && active(next))
          timer = setTimeout(() => {
            if (document.visibilityState !== "hidden") void refresh();
          }, 2500);
      } catch (cause) {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Could not refresh identification.",
          );
      } finally {
        running = false;
      }
    }
    const visible = () => {
      if (document.visibilityState !== "hidden") {
        clearTimeout(timer);
        void refresh();
      }
    };
    void refresh();
    document.addEventListener("visibilitychange", visible);
    return () => {
      controller.abort();
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", visible);
    };
  }, [article.id, article.version, api, revision]);
  async function identify() {
    if (busy || uncertain || (value && active(value))) return;
    const controller = new AbortController();
    mutation.current = controller;
    setBusy(true);
    setError("");
    try {
      const next = await api.request(article.id, controller.signal);
      if (!controller.signal.aborted) {
        setValue(next);
        setRevision((v) => v + 1);
      }
    } catch (cause) {
      if (!controller.signal.aborted) {
        setUncertain(true);
        setError(
          cause instanceof Error
            ? cause.message
            : "Request not confirmed. Refresh status before trying again.",
        );
      }
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  const manual =
    value?.categorySource === "USER" && value.nameSource === "USER";
  return (
    <section className={styles.panel} aria-label="AI identification">
      <div className={styles.heading}>
        <h3>AI identification</h3>
        {value?.provider && (
          <span className={styles.provider}>
            {value.provider === "groq"
              ? "Groq"
              : value.provider === "openrouter"
                ? "OpenRouter"
                : value.provider}
          </span>
        )}
      </div>
      <p
        role="status"
        className={
          value?.status === "FAILED" && !manual ? styles.failure : undefined
        }
      >
        {value
          ? message(value)
          : error
            ? "Identification status unavailable."
            : "Checking identification…"}
      </p>
      {value && (
        <dl className={styles.sources}>
          <dt>Category set by</dt>
          <dd data-source={value.categorySource}>
            {source(value.categorySource)}
          </dd>
          <dt>Name set by</dt>
          <dd data-source={value.nameSource}>{source(value.nameSource)}</dd>
        </dl>
      )}
      {!!value?.subjects.length && (
        <ul className={styles.subjects} aria-label="Identified subjects">
          {value.subjects.map((s) => (
            <li key={s.name}>
              <span>{s.name}</span>
              {s.main && <strong>Main subject</strong>}
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p role="alert" className={styles.failure}>
          {error}
        </p>
      )}
      <div className={styles.actions}>
        {value && article.content && !manual && (
          <button
            type="button"
            disabled={busy || uncertain || active(value)}
            onClick={() => void identify()}
          >
            {busy
              ? "Requesting…"
              : active(value)
                ? "Identification in progress"
                : value.status === "FAILED"
                  ? "Retry identification"
                  : value.status === "SUCCEEDED"
                    ? "Identify again"
                    : "Identify missing details"}
          </button>
        )}
        {error && (
          <button
            type="button"
            disabled={busy}
            onClick={() => setRevision((v) => v + 1)}
          >
            Refresh status
          </button>
        )}
      </div>
      <small>
        Your edits always take priority. AI never changes your entity links.
      </small>
    </section>
  );
}
