import { useEffect, useRef, useState } from "react";
import {
  requiresCategoryReload,
  type CategoryApi,
  type ManagedCategory,
} from "./api";
import styles from "./CategoriesPage.module.css";

export function CategoriesPage({
  api,
  onChanged,
  onArticles,
}: {
  api: CategoryApi;
  onChanged: (items: ManagedCategory[]) => void;
  onArticles: (key: string) => void;
}) {
  const [items, setItems] = useState<ManagedCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState("");
  const [editing, setEditing] = useState<ManagedCategory | "new" | null>(null);
  const [removing, setRemoving] = useState<ManagedCategory | null>(null);
  const [busy, setBusy] = useState(false);
  const request = useRef<AbortController | null>(null);
  const changed = useRef(onChanged);
  changed.current = onChanged;
  const blocked = busy || loading || requiresCategoryReload(error);
  async function load() {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setLoading(true);
    setError(null);
    try {
      const rows = await api.list(controller.signal);
      if (!controller.signal.aborted) {
        setItems(rows);
        changed.current(rows);
      }
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause);
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }
  useEffect(() => {
    document.title = "Categories · Know";
    void load();
    return () => request.current?.abort();
  }, [api]);
  function update(rows: ManagedCategory[], message: string) {
    setItems(rows);
    changed.current(rows);
    setNotice(message);
    setError(null);
  }
  async function toggle(current: ManagedCategory) {
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      const result = await api.edit(
        current,
        { enabled: !current.enabled },
        controller.signal,
      );
      if (!controller.signal.aborted)
        update(
          items.map((c) => (c.key === result.key ? result : c)),
          `${result.displayName} ${result.enabled ? "enabled" : "disabled"}.`,
        );
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  return (
    <main id="main-content" tabIndex={-1} className={styles.page}>
      <div className={styles.heading}>
        <div>
          <h1>Categories</h1>
          <p>
            Organize your saved entries. Categories with articles cannot be
            disabled or removed.
          </p>
        </div>
        <div className={styles.actions}>
          <button
            className={styles.button}
            disabled={loading || busy}
            onClick={() => void load()}
          >
            Reload categories
          </button>
          <button
            className={`${styles.button} ${styles.primary}`}
            disabled={loading || busy || requiresCategoryReload(error)}
            onClick={() => setEditing("new")}
          >
            New category
          </button>
        </div>
      </div>
      {loading && (
        <p role="status" className={styles.message}>
          Loading categories…
        </p>
      )}
      {notice && (
        <p role="status" className={styles.message}>
          {notice}
        </p>
      )}
      {error instanceof Error && (
        <p role="alert" className={`${styles.message} ${styles.error}`}>
          {error.message}
        </p>
      )}
      {!loading && items.length > 0 && (
        <>
          <div className={styles.columns} aria-hidden="true">
            <span>Category</span>
            <span>Type & status</span>
            <span>Articles</span>
            <span>Actions</span>
          </div>
          <ul className={styles.list} aria-label="Categories">
            {items.map((c) => (
              <li key={c.key} className={styles.row}>
                <strong className={styles.name}>
                  <i
                    className={styles.dot}
                    style={{ background: c.color }}
                    aria-hidden="true"
                  />
                  {c.displayName}
                </strong>
                <span className={styles.badge}>
                  {c.isDefault ? "Predefined" : "Custom"}
                  <span className={styles.status}>
                    {c.enabled ? "Enabled" : "Disabled"}
                  </span>
                </span>
                {c.articleCount > 0 ? (
                  <button
                    className={styles.count}
                    onClick={() => onArticles(c.key)}
                    aria-label={`View ${c.articleCount} articles in ${c.displayName}`}
                  >
                    {c.articleCount}{" "}
                    {c.articleCount === 1 ? "article" : "articles"}
                  </button>
                ) : (
                  <span className={styles.note}>0 articles</span>
                )}
                <div className={styles.actions}>
                  <button
                    className={styles.button}
                    disabled={blocked}
                    onClick={() => setEditing(c)}
                    aria-label={`Edit ${c.displayName}`}
                  >
                    Edit
                  </button>
                  {c.key === "OTHER" ? (
                    <span className={styles.note}>Always enabled</span>
                  ) : (
                    <button
                      className={styles.button}
                      disabled={blocked || (c.enabled && c.articleCount > 0)}
                      title={
                        c.enabled && c.articleCount > 0
                          ? "Remove or move its articles first"
                          : undefined
                      }
                      onClick={() => void toggle(c)}
                      aria-label={`${c.enabled ? "Disable" : "Enable"} ${c.displayName}`}
                    >
                      {c.enabled ? "Disable" : "Enable"}
                    </button>
                  )}
                  {!c.isDefault && (
                    <button
                      className={`${styles.button} ${styles.danger}`}
                      disabled={blocked || c.articleCount > 0}
                      title={
                        c.articleCount > 0
                          ? "Remove or move its articles first"
                          : undefined
                      }
                      onClick={() => setRemoving(c)}
                      aria-label={`Remove ${c.displayName}`}
                    >
                      Remove
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
      {editing && (
        <CategoryForm
          current={editing === "new" ? null : editing}
          api={api}
          onClose={() => setEditing(null)}
          onSaved={(result) => {
            update(
              editing === "new"
                ? [...items, result]
                : items.map((c) => (c.key === result.key ? result : c)),
              "Category saved.",
            );
            setEditing(null);
          }}
          onReload={() => {
            setEditing(null);
            void load();
          }}
        />
      )}
      {removing && (
        <RemoveCategory
          current={removing}
          api={api}
          onClose={() => setRemoving(null)}
          onDeleted={() => {
            update(
              items.filter((c) => c.key !== removing.key),
              "Category removed.",
            );
            setRemoving(null);
          }}
          onReload={() => {
            setRemoving(null);
            void load();
          }}
        />
      )}
    </main>
  );
}

function useDialog() {
  const dialog = useRef<HTMLDialogElement>(null);
  const request = useRef<AbortController | null>(null);
  useEffect(() => {
    const node = dialog.current;
    node?.showModal();
    return () => {
      request.current?.abort();
      node?.close();
    };
  }, []);
  return { dialog, request };
}
function CategoryForm({
  current,
  api,
  onClose,
  onSaved,
  onReload,
}: {
  current: ManagedCategory | null;
  api: CategoryApi;
  onClose: () => void;
  onSaved: (c: ManagedCategory) => void;
  onReload: () => void;
}) {
  const { dialog, request } = useDialog();
  const [name, setName] = useState(current?.displayName ?? "");
  const [color, setColor] = useState(current?.color ?? "#305ea7");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  async function save(event: React.FormEvent) {
    event.preventDefault();
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(null);
    try {
      const result = current
        ? await api.edit(
            current,
            { displayName: name, color },
            controller.signal,
          )
        : await api.create(name, color, controller.signal);
      if (!controller.signal.aborted) onSaved(result);
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
      aria-labelledby="category-form-title"
      onCancel={(event) => {
        if (busy) event.preventDefault();
        else onClose();
      }}
    >
      <h2 id="category-form-title">
        {current ? "Edit category" : "New category"}
      </h2>
      <form className={styles.form} onSubmit={(event) => void save(event)}>
        <label>
          Name
          <input
            autoFocus
            required
            value={name}
            disabled={busy}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <div className={styles.color}>
          <label>
            Color
            <input
              type="color"
              value={/^#[0-9a-f]{6}$/i.test(color) ? color : "#305ea7"}
              disabled={busy}
              onChange={(event) => setColor(event.target.value)}
            />
          </label>
          <label>
            Hex color
            <input
              required
              pattern="#[0-9a-fA-F]{6}"
              value={color}
              disabled={busy}
              onChange={(event) => setColor(event.target.value)}
            />
          </label>
        </div>
        {error instanceof Error && (
          <p role="alert" className={styles.error}>
            {error.message}
          </p>
        )}
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.button}
            disabled={busy}
            onClick={onClose}
          >
            Cancel
          </button>
          {requiresCategoryReload(error) ? (
            <button type="button" className={styles.button} onClick={onReload}>
              Reload categories
            </button>
          ) : (
            <button
              className={`${styles.button} ${styles.primary}`}
              disabled={busy || !name.trim()}
            >
              {busy ? "Saving…" : "Save category"}
            </button>
          )}
        </div>
      </form>
    </dialog>
  );
}
function RemoveCategory({
  current,
  api,
  onClose,
  onDeleted,
  onReload,
}: {
  current: ManagedCategory;
  api: CategoryApi;
  onClose: () => void;
  onDeleted: () => void;
  onReload: () => void;
}) {
  const { dialog, request } = useDialog();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  async function remove() {
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(null);
    try {
      await api.remove(current, controller.signal);
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
      aria-labelledby="category-remove-title"
      onCancel={(event) => {
        if (busy) event.preventDefault();
        else onClose();
      }}
    >
      <h2 id="category-remove-title">Remove {current.displayName}?</h2>
      <p>This empty category will be permanently removed.</p>
      {error instanceof Error && (
        <p role="alert" className={styles.error}>
          {error.message}
        </p>
      )}
      <div className={styles.actions}>
        <button
          className={styles.button}
          disabled={busy}
          onClick={onClose}
          autoFocus
        >
          Cancel
        </button>
        {requiresCategoryReload(error) ? (
          <button className={styles.button} onClick={onReload}>
            Reload categories
          </button>
        ) : (
          <button
            className={`${styles.button} ${styles.danger}`}
            disabled={busy}
            onClick={() => void remove()}
          >
            {busy ? "Removing…" : "Remove category"}
          </button>
        )}
      </div>
    </dialog>
  );
}
