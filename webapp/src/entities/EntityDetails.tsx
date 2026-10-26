import { useCallback, useEffect, useRef, useState } from "react";
import type { EntityApi } from "./api";
import { requiresReload } from "./api";
import { types, statusLabel, type Entity } from "./model";
import { usePage } from "./usePage";
import { CompletionHistory } from "./CompletionHistory";
import { exactCaptureTime } from "../timeline/dates";
import styles from "./Entities.module.css";

export function EntityDetails({
  id,
  api,
  revision,
  onChanged,
  onBack,
  onOpenArticle,
  focus = false,
}: {
  id: string;
  api: EntityApi;
  revision: number;
  onChanged: () => void;
  onBack: () => void;
  onOpenArticle: (id: string) => void;
  focus?: boolean;
}) {
  const [entity, setEntity] = useState<Entity | null>(null),
    [title, setTitle] = useState(""),
    [status, setStatus] = useState(""),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null),
    [reload, setReload] = useState(0);
  const previous = useRef<Entity | null>(null),
    write = useRef<AbortController | null>(null),
    panel = useRef<HTMLElement>(null);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    if (previous.current?.id !== id) {
      setEntity(null);
      previous.current = null;
    }
    void api
      .read(id, controller.signal)
      .then((fresh) => {
        if (controller.signal.aborted) return;
        const old = previous.current;
        setTitle((value) => (old && value !== old.title ? value : fresh.title));
        setStatus((value) =>
          old && value !== old.status ? value : fresh.status,
        );
        setEntity(fresh);
        previous.current = fresh;
      })
      .catch((cause) => {
        if (!controller.signal.aborted) setError(cause);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id, api, revision, reload]);
  useEffect(() => {
    if (focus) panel.current?.focus({ preventScroll: true });
  }, [id, focus]);
  useEffect(() => () => write.current?.abort(), []);
  function refresh() {
    previous.current = null;
    setReload((n) => n + 1);
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!entity) return;
    if (!title.trim()) {
      setError(new Error("Enter a title."));
      return;
    }
    const controller = new AbortController();
    write.current = controller;
    setBusy(true);
    setError(null);
    try {
      const fresh = await api.update(
        entity,
        { title, status },
        controller.signal,
      );
      if (!controller.signal.aborted) {
        setEntity(fresh);
        previous.current = fresh;
        setTitle(fresh.title);
        setStatus(fresh.status);
        onChanged();
      }
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  const sourceLoad = useCallback(
    (cursor: string | null, signal: AbortSignal) =>
      api.sources(id, cursor, signal),
    [api, id, revision],
  );
  const sources = usePage(sourceLoad);
  const config = entity ? types[entity.type] : null;
  const blocked = busy || loading || requiresReload(error);
  return (
    <aside
      ref={panel}
      tabIndex={-1}
      className={styles.detail}
      aria-label="Entity details"
    >
      <button
        className={`${styles.action} ${styles.mobileBack}`}
        onClick={onBack}
      >
        ← {config?.label ?? "Entities"}
      </button>
      {loading && <p role="status">Loading entity…</p>}
      {error instanceof Error && (
        <div role="alert" className={styles.error}>
          {error.message}
          <button className={styles.action} onClick={refresh}>
            Reload entity
          </button>
        </div>
      )}
      {entity && config && (
        <>
          <h3>
            {config.singular[0].toUpperCase() + config.singular.slice(1)}{" "}
            details
          </h3>
          <form className={styles.form} onSubmit={(event) => void save(event)}>
            <label>
              Title
              <input
                required
                value={title}
                disabled={blocked}
                onChange={(event) => setTitle(event.target.value)}
              />
            </label>
            <label>
              Created
              <time
                className={styles.createdValue}
                dateTime={entity.createdAt}
                title={entity.createdAt}
              >
                {exactCaptureTime(entity.createdAt)}
              </time>
            </label>
            <label>
              Status
              <select
                value={status}
                disabled={blocked}
                onChange={(event) => setStatus(event.target.value)}
              >
                {config.statuses.map((s) => (
                  <option key={s} value={s}>
                    {statusLabel(s)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Last completion
              <span className={styles.createdValue}>
                {entity.lastCompletedAt
                  ? exactCaptureTime(entity.lastCompletedAt)
                  : "—"}
              </span>
            </label>
            <div className={styles.historyActions}>
              <button className={styles.action} disabled={blocked}>
                {busy ? "Saving…" : "Save"}
              </button>
              <span className={styles.help}>Editable title & status</span>
            </div>
          </form>
          <CompletionHistory
            key={entity.id}
            entity={entity}
            api={api}
            revision={revision + reload}
            onChanged={onChanged}
            onReload={refresh}
            disabled={
              blocked || title !== entity.title || status !== entity.status
            }
          />
          <section
            className={styles.linked}
            aria-label="Linked timeline entries"
          >
            <h4>Linked timeline entries</h4>
            {sources.loading && <p role="status">Loading sources…</p>}
            {sources.error && (
              <p role="alert">
                {sources.error}
                <button className={styles.textAction} onClick={sources.retry}>
                  Try again
                </button>
              </p>
            )}
            {!sources.loading && !sources.error && !sources.items.length && (
              <p>No linked page.</p>
            )}
            {sources.items.map((source) => (
              <a
                key={source.id}
                href={`#article/${source.id}`}
                onClick={(event) => {
                  event.preventDefault();
                  onOpenArticle(source.id);
                }}
              >
                {source.title} ↗
              </a>
            ))}
            {sources.cursor && !sources.error && (
              <button
                className={styles.action}
                disabled={sources.loading}
                onClick={sources.more}
              >
                Load more sources
              </button>
            )}
          </section>
        </>
      )}
    </aside>
  );
}
