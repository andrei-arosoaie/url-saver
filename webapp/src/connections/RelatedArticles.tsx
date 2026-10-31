import { useEffect, useRef, useState } from "react";
import type { Article } from "../api";
import {
  pending,
  type ConnectionPage,
  type ConnectionsApi,
  type Processing,
} from "./api";
import styles from "./RelatedArticles.module.css";

function statusText(state: Processing, count: number) {
  switch (state.state) {
    case "DISABLED":
      return "Article connections are not enabled.";
    case "SKIPPED":
      return "Connections need saved article text.";
    case "QUEUED":
      return "Finding related articles — queued.";
    case "PROCESSING":
      return "Finding related articles…";
    case "PAUSED":
      return state.reason === "LIVE_REQUESTS_DISABLED"
        ? "Semantic matching is disabled on the server. Shared-entity results remain available."
        : "Provider quota reached. Processing will resume automatically.";
    case "RETRY_PENDING":
      return state.reason === "LIVE_REQUESTS_DISABLED"
        ? "Semantic matching is paused. Shared-entity results remain available."
        : state.reason === "PROVIDER_BUSY"
          ? "Waiting for provider capacity."
          : state.reason === "DAILY_BUDGET"
            ? "Daily matching budget reached. Processing will resume automatically."
            : "Matching will retry automatically.";
    case "FAILED":
      return "Matching failed. Existing connections are kept.";
    case "COMPLETE":
      return count
        ? "Matching complete for this article."
        : "No related articles yet.";
  }
}
function host(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

export function RelatedArticles({
  article,
  api,
  linkRevision = 0,
  onOpen,
}: {
  article: Article;
  api: ConnectionsApi;
  linkRevision?: number;
  onOpen: (id: string) => void;
}) {
  const [page, setPage] = useState<ConnectionPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [confirm, setConfirm] = useState<string | null>(null);
  const depth = useRef(1);
  const current = useRef<ConnectionPage | null>(null);
  const mutation = useRef<AbortController | null>(null);
  const read = useRef<AbortController | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const origin = useRef<HTMLButtonElement | null>(null);
  const loadMore = useRef<() => void>(() => {});
  const recovering = useRef(false);
  useEffect(() => {
    mutation.current?.abort();
    mutation.current = null;
    current.current = null;
    depth.current = 1;
    setPage(null);
    setConfirm(null);
    setError("");
    setActionError("");
    setUncertain(false);
    setBusy(null);
    setNotice("");
  }, [article.id]);
  useEffect(() => () => mutation.current?.abort(), []);
  useEffect(() => {
    const lifecycle = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let running = false;
    async function refresh(more = false) {
      if (running || lifecycle.signal.aborted || mutation.current) return;
      running = true;
      clearTimeout(timer);
      const controller = new AbortController();
      read.current = controller;
      const signal = AbortSignal.any([controller.signal, lifecycle.signal]);
      setLoading(true);
      try {
        let next: ConnectionPage;
        if (more && current.current?.nextCursor) {
          const extra = await api.list(
            article.id,
            current.current.nextCursor,
            signal,
          );
          next = {
            ...extra,
            items: [...current.current.items, ...extra.items],
          };
          if (!signal.aborted) depth.current++;
        } else {
          next = await api.list(article.id, null, signal);
          for (
            let n = 1;
            n < depth.current && next.nextCursor && !signal.aborted;
            n++
          ) {
            const extra = await api.list(article.id, next.nextCursor, signal);
            next = { ...extra, items: [...next.items, ...extra.items] };
          }
        }
        if (signal.aborted) return;
        next.items = [
          ...new Map(next.items.map((item) => [item.id, item])).values(),
        ];
        current.current = next;
        setPage(next);
        setError("");
        if (recovering.current) {
          setUncertain(false);
          setActionError("");
          recovering.current = false;
        }
      } catch (cause) {
        if (!signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Could not load related articles.",
          );
      } finally {
        running = false;
        if (!signal.aborted) {
          setLoading(false);
          const state = current.current?.processing;
          if (state && pending(state.state))
            timer = setTimeout(() => {
              if (document.visibilityState !== "hidden") void refresh();
            }, state.pollIntervalMs);
        }
      }
    }
    const visible = () => {
      if (document.visibilityState !== "hidden") void refresh();
    };
    loadMore.current = () => {
      void refresh(true);
    };
    void refresh();
    document.addEventListener("visibilitychange", visible);
    return () => {
      lifecycle.abort();
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", visible);
    };
  }, [article.id, article.version, api, revision, linkRevision]);

  async function mutate(relatedId?: string) {
    if (busy || uncertain) return;
    read.current?.abort();
    const controller = new AbortController();
    mutation.current = controller;
    setBusy(relatedId ?? "retry");
    setError("");
    setActionError("");
    setNotice("");
    try {
      if (relatedId) await api.remove(article.id, relatedId, controller.signal);
      else await api.retry(article.id, controller.signal);
      if (controller.signal.aborted) return;
      if (relatedId && current.current) {
        current.current = {
          ...current.current,
          items: current.current.items.filter((item) => item.id !== relatedId),
        };
        setPage(current.current);
        setConfirm(null);
        setNotice(
          "Connection removed from both articles. It will not be recreated automatically.",
        );
        heading.current?.focus({ preventScroll: true });
      } else setNotice("Matching retry requested.");
    } catch (cause) {
      if (!controller.signal.aborted) {
        setUncertain(true);
        setActionError(
          cause instanceof Error
            ? cause.message
            : "Request not confirmed. Refresh connections before trying again.",
        );
      }
    } finally {
      if (!controller.signal.aborted) {
        mutation.current = null;
        setBusy(null);
        setRevision((value) => value + 1);
      }
    }
  }
  const disabled = Boolean(busy) || uncertain;
  return (
    <section className={styles.section} aria-label="Related articles">
      <div className={styles.heading}>
        <h3 ref={heading} tabIndex={-1}>
          Related articles
        </h3>
        <button
          type="button"
          disabled={loading || Boolean(busy)}
          onClick={() => {
            recovering.current = true;
            setRevision((value) => value + 1);
          }}
        >
          Refresh connections
        </button>
      </div>
      <p
        role="status"
        className={
          page?.processing.state === "FAILED" ? styles.error : styles.status
        }
      >
        {page
          ? statusText(page.processing, page.items.length)
          : error
            ? "Related articles unavailable."
            : "Checking related articles…"}
        {page?.processing.nextAttemptAt && pending(page.processing.state) && (
          <>
            {" "}
            Next attempt:{" "}
            <time dateTime={page.processing.nextAttemptAt}>
              {new Date(page.processing.nextAttemptAt).toLocaleString()}
            </time>
            .
          </>
        )}
      </p>
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
      {actionError && (
        <p role="alert" className={styles.error}>
          {actionError}
        </p>
      )}
      {notice && (
        <p role="status" className={styles.notice}>
          {notice}
        </p>
      )}
      {page?.processing.state === "FAILED" && (
        <button
          className={styles.retry}
          type="button"
          disabled={disabled}
          onClick={() => void mutate()}
        >
          {busy === "retry" ? "Requesting retry…" : "Retry matching"}
        </button>
      )}
      {!!page?.items.length && (
        <ul className={styles.list}>
          {page.items.map((item) => (
            <li key={item.id} className={styles.card}>
              <a
                className={styles.title}
                href={`#article/${item.id}`}
                onClick={(event) => {
                  if (
                    event.button === 0 &&
                    !event.metaKey &&
                    !event.ctrlKey &&
                    !event.altKey &&
                    !event.shiftKey
                  ) {
                    event.preventDefault();
                    onOpen(item.id);
                  }
                }}
              >
                {item.entityName?.trim() ? item.entityName : item.title}
              </a>
              <div className={styles.meta}>
                <span>{host(item.url)}</span>
                <time dateTime={item.savedAt}>
                  {new Date(item.savedAt).toLocaleDateString()}
                </time>
              </div>
              <ul
                className={styles.reasons}
                aria-label={`Connection reasons for ${item.entityName || item.title}`}
              >
                {item.sharedEntities.map((entity) => (
                  <li key={entity.id}>Both mention {entity.name}</li>
                ))}
                {item.semanticScore !== null && <li>Similar content</li>}
              </ul>
              {item.semanticUpdating && (
                <p className={styles.status}>Updating similarity…</p>
              )}
              {confirm === item.id ? (
                <div
                  className={styles.confirm}
                  role="group"
                  aria-label="Confirm connection removal"
                >
                  <p>
                    Remove this connection permanently? Both articles stay
                    saved.
                  </p>
                  <button
                    autoFocus
                    type="button"
                    disabled={disabled}
                    onClick={() => void mutate(item.id)}
                  >
                    {busy === item.id ? "Removing…" : "Confirm removal"}
                  </button>
                  <button
                    type="button"
                    disabled={Boolean(busy)}
                    onClick={() => {
                      setConfirm(null);
                      queueMicrotask(() => origin.current?.focus());
                    }}
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  className={styles.remove}
                  type="button"
                  disabled={disabled}
                  aria-label={`Remove connection to ${item.entityName || item.title}`}
                  onClick={(event) => {
                    origin.current = event.currentTarget;
                    setConfirm(item.id);
                  }}
                >
                  Remove connection
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {page?.nextCursor && (
        <button
          className={styles.more}
          type="button"
          disabled={loading || Boolean(busy)}
          onClick={() => loadMore.current()}
        >
          {loading ? "Loading related articles…" : "Load more related articles"}
        </button>
      )}
    </section>
  );
}
