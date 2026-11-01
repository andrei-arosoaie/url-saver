import { useCallback, useEffect, useRef, useState } from "react";
import type { Entity, Completion } from "./model";
import { fromUtcInput, utcInput } from "./model";
import type { EntityApi } from "./api";
import { requiresReload } from "./api";
import { usePage } from "./usePage";
import { exactCaptureTime } from "../timeline/dates";
import styles from "./Entities.module.css";
import { CompletionDateInput } from "./CompletionDateInput";

export function CompletionHistory({
  entity,
  api,
  revision,
  onChanged,
  onReload,
  disabled = false,
}: {
  entity: Entity;
  api: EntityApi;
  revision: number;
  onChanged: () => void;
  onReload: () => void;
  disabled?: boolean;
}) {
  const load = useCallback(
    (cursor: string | null, signal: AbortSignal) =>
      api.history(entity.id, cursor, signal),
    [api, entity.id, revision],
  );
  const page = usePage(load);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null),
    [dated, setDated] = useState(false),
    [time, setTime] = useState("");
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => {
    setError(null);
  }, [entity.version]);
  async function mutate(action: (signal: AbortSignal) => Promise<unknown>) {
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(null);
    try {
      await action(controller.signal);
      if (!controller.signal.aborted) {
        setDated(false);
        setTime("");
        onChanged();
      }
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  const blocked = disabled || busy || requiresReload(error);
  return (
    <section className={styles.linked} aria-label="Completion history">
      <h4>
        Completion history{" "}
        <span className={styles.muted}>({entity.totalCompletionCount})</span>
      </h4>
      <div className={styles.historyActions}>
        <button
          className={styles.action}
          disabled={blocked}
          onClick={() =>
            void mutate((signal) => api.complete(entity, undefined, signal))
          }
        >
          ＋ Add completion
        </button>
        <button
          className={styles.textAction}
          disabled={blocked}
          onClick={() => setDated(!dated)}
        >
          Record a past completion
        </button>
      </div>
      {dated && (
        <form
          className={styles.form}
          onSubmit={(event) => {
            event.preventDefault();
            try {
              const timestamp = fromUtcInput(time);
              void mutate((signal) => api.complete(entity, timestamp, signal));
            } catch (cause) {
              setError(cause);
            }
          }}
        >
          <CompletionDateInput
            label="Completed at (UTC)"
            value={time}
            onChange={setTime}
            disabled={blocked}
          />
          <button className={styles.action} disabled={blocked}>
            Record completion
          </button>
        </form>
      )}
      {error instanceof Error && (
        <div role="alert" className={styles.error}>
          {error.message}
          {requiresReload(error) && (
            <button className={styles.action} onClick={onReload}>
              Reload entity
            </button>
          )}
        </div>
      )}
      {page.loading && <p role="status">Loading history…</p>}
      {page.error && (
        <p role="alert">
          {page.error}{" "}
          <button className={styles.textAction} onClick={page.retry}>
            Try again
          </button>
        </p>
      )}
      {!page.loading && !page.error && !page.items.length && (
        <p>No completions recorded.</p>
      )}
      <ol className={styles.history}>
        {page.items.map((event) => (
          <HistoryEvent
            key={`${event.id}:${event.version}`}
            event={event}
            blocked={blocked}
            onCorrect={(value) =>
              void mutate((signal) => api.correct(event, value, signal))
            }
            onDelete={() =>
              void mutate((signal) => api.deleteCompletion(event, signal))
            }
          />
        ))}
      </ol>
      {page.cursor && !page.error && (
        <button
          className={styles.action}
          disabled={page.loading || busy}
          onClick={page.more}
        >
          Load more history
        </button>
      )}
    </section>
  );
}
function HistoryEvent({
  event,
  blocked,
  onCorrect,
  onDelete,
}: {
  event: Completion;
  blocked: boolean;
  onCorrect: (time: string) => void;
  onDelete: () => void;
}) {
  const [editing, setEditing] = useState(false),
    [value, setValue] = useState(utcInput(event.completedAt)),
    [error, setError] = useState("");
  return (
    <li>
      <time dateTime={event.completedAt} title={event.completedAt}>
        {exactCaptureTime(event.completedAt)}
      </time>
      <small className={styles.utc}>{event.completedAt}</small>
      {editing ? (
        <form
          className={styles.form}
          onSubmit={(e) => {
            e.preventDefault();
            try {
              onCorrect(fromUtcInput(value));
              setEditing(false);
            } catch (cause) {
              setError(
                cause instanceof Error ? cause.message : "Invalid timestamp.",
              );
            }
          }}
        >
          <CompletionDateInput
            label="Correct completed at (UTC)"
            value={value}
            onChange={setValue}
            disabled={blocked}
          />
          {error && <p role="alert">{error}</p>}
          <div className={styles.historyActions}>
            <button className={styles.action} disabled={blocked}>
              Save timestamp
            </button>
            <button
              className={styles.textAction}
              type="button"
              onClick={() => setEditing(false)}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <div className={styles.historyActions}>
          <button
            className={styles.textAction}
            disabled={blocked}
            onClick={() => setEditing(true)}
          >
            Edit timestamp
          </button>
          <button
            className={styles.textAction}
            disabled={blocked}
            onClick={onDelete}
          >
            Remove completion
          </button>
        </div>
      )}
    </li>
  );
}
