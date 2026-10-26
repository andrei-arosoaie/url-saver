import { useEffect, useRef, useState } from "react";
import { EntityApiError, requiresReload, type EntityApi } from "./api";
import { types, statusLabel, type EntityType, type Entity } from "./model";
import styles from "./Entities.module.css";
export function ManualEntity({
  type,
  api,
  onClose,
  onCreated,
  onExisting,
}: {
  type: EntityType;
  api: EntityApi;
  onClose: () => void;
  onCreated: (entity: Entity) => void;
  onExisting: (id: string) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null),
    request = useRef<AbortController | null>(null);
  const [title, setTitle] = useState(""),
    [status, setStatus] = useState<string>(types[type].statuses[0]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null);
  useEffect(() => {
    dialog.current?.showModal();
    return () => {
      request.current?.abort();
      dialog.current?.close();
    };
  }, []);
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!title.trim()) {
      setError(new Error("Enter a title."));
      return;
    }
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(null);
    try {
      const entity = await api.create(
        { type, title, status },
        controller.signal,
      );
      if (!controller.signal.aborted) onCreated(entity);
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  return (
    <dialog
      ref={dialog}
      className={styles.modal}
      aria-labelledby="manual-heading"
      onCancel={(event) => {
        if (busy) event.preventDefault();
        else onClose();
      }}
    >
      <h2 id="manual-heading">Add {types[type].singular}</h2>
      <form className={styles.form} onSubmit={(event) => void save(event)}>
        <label>
          Title
          <input
            autoFocus
            required
            value={title}
            disabled={busy}
            onChange={(event) => setTitle(event.target.value)}
          />
        </label>
        <label>
          Status
          <select
            value={status}
            disabled={busy}
            onChange={(event) => setStatus(event.target.value)}
          >
            {types[type].statuses.map((s) => (
              <option key={s} value={s}>
                {statusLabel(s)}
              </option>
            ))}
          </select>
        </label>
        {error instanceof Error && (
          <p role="alert" className={styles.error}>
            {error.message}
          </p>
        )}
        {error instanceof EntityApiError && error.existingEntityId && (
          <button
            type="button"
            className={styles.action}
            onClick={() => onExisting(error.existingEntityId!)}
          >
            Open existing {types[type].singular}
          </button>
        )}
        <div className={styles.modalActions}>
          <button
            type="button"
            className={styles.action}
            onClick={onClose}
            disabled={busy}
          >
            Cancel
          </button>
          <button
            type="submit"
            className={styles.action}
            disabled={busy || requiresReload(error)}
          >
            {busy ? "Adding…" : "Add"}
          </button>
        </div>
        {requiresReload(error) && (
          <p className={styles.help}>
            Close this form and refresh the list before trying again.
          </p>
        )}
      </form>
    </dialog>
  );
}
