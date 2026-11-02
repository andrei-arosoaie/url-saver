import { useEffect, useRef, useState } from "react";
import { requiresReload, type EntityApi } from "./api";
import type { Entity } from "./model";
import styles from "../categories/CategoriesPage.module.css";

export function DeleteEntity({
  entity,
  remove,
  onDeleted,
  onReload,
  disabled = false,
}: {
  entity: Entity;
  remove: EntityApi["remove"];
  onDeleted: () => void;
  onReload: () => void;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        className={`${styles.button} ${styles.danger}`}
        disabled={disabled}
        onClick={() => setOpen(true)}
      >
        Delete entity
      </button>
      {open && (
        <DeleteConfirmation
          entity={entity}
          remove={remove}
          onClose={() => setOpen(false)}
          onDeleted={onDeleted}
          onReload={() => {
            setOpen(false);
            onReload();
          }}
        />
      )}
    </>
  );
}
function DeleteConfirmation({
  entity,
  remove,
  onClose,
  onDeleted,
  onReload,
}: {
  entity: Entity;
  remove: EntityApi["remove"];
  onClose: () => void;
  onDeleted: () => void;
  onReload: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const request = useRef<AbortController | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    const node = dialog.current;
    node?.showModal();
    return () => {
      request.current?.abort();
      node?.close();
    };
  }, []);
  async function confirm() {
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(null);
    try {
      await remove(entity, controller.signal);
      if (!controller.signal.aborted) onDeleted();
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
      aria-labelledby="entity-delete-title"
      onCancel={(event) => {
        if (busy) event.preventDefault();
        else onClose();
      }}
    >
      <h2 id="entity-delete-title">Delete this entity?</h2>
      <p>
        <strong>{entity.title}</strong>
      </p>
      <p>
        This permanently deletes this entity, its completion history, viewing
        progress and article associations. Linked articles and their saved
        content stay.
      </p>
      {error instanceof Error && (
        <p role="alert" className={styles.error}>
          {error.message}
        </p>
      )}
      <div className={styles.actions}>
        <button
          className={styles.button}
          autoFocus
          disabled={busy}
          onClick={onClose}
        >
          Cancel
        </button>
        {requiresReload(error) ? (
          <button className={styles.button} onClick={onReload}>
            Reload entity
          </button>
        ) : (
          <button
            className={`${styles.button} ${styles.danger}`}
            disabled={busy}
            onClick={() => void confirm()}
          >
            {busy ? "Deleting…" : "Delete permanently"}
          </button>
        )}
      </div>
    </dialog>
  );
}
