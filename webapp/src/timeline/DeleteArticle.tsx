import { useEffect, useRef, useState } from "react";
import { ArticleConflict, type Article, type LibraryApi } from "../api";
import styles from "../categories/CategoriesPage.module.css";

export function DeleteArticle({
  article,
  remove,
  onDeleted,
  onReload,
}: {
  article: Article;
  remove: NonNullable<LibraryApi["deleteArticle"]>;
  onDeleted: () => void;
  onReload: () => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        className={`${styles.button} ${styles.danger}`}
        onClick={() => setOpen(true)}
      >
        Delete article
      </button>
      {open && (
        <DeleteConfirmation
          article={article}
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
  article,
  remove,
  onClose,
  onDeleted,
  onReload,
}: {
  article: Article;
  remove: NonNullable<LibraryApi["deleteArticle"]>;
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
      await remove(article, controller.signal);
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
      aria-labelledby="article-delete-title"
      onCancel={(event) => {
        if (busy) event.preventDefault();
        else onClose();
      }}
    >
      <h2 id="article-delete-title">Delete this article?</h2>
      <p>
        <strong>{article.entityName || article.title}</strong>
      </p>
      <p>
        This permanently deletes the saved article, its screenshot and its
        entity associations. Linked entities and their history stay.
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
        {error instanceof ArticleConflict ? (
          <button className={styles.button} onClick={onReload}>
            Reload article
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
