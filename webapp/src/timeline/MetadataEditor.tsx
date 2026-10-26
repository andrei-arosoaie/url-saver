import { useEffect, useRef, useState } from "react";
import type { Article, Category, LibraryApi } from "../api";
import { ArticleConflict } from "../api";
import styles from "./MetadataEditor.module.css";

export function MetadataEditor({
  article,
  categories,
  api,
  onSaved,
}: {
  article: Article;
  categories: Category[];
  api: LibraryApi;
  onSaved: (article: Article) => void;
}) {
  const [open, setOpen] = useState(false);
  const [category, setCategory] = useState(article.category ?? "OTHER");
  const [name, setName] = useState(article.entityName ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [conflict, setConflict] = useState(false);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  if (!api.updateMetadata || !categories.length) return null;
  async function save(event: React.FormEvent) {
    event.preventDefault();
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError("");
    try {
      const updated = await api.updateMetadata!(
        article,
        category,
        name === "" ? null : name,
        controller.signal,
      );
      if (!controller.signal.aborted) {
        onSaved(updated);
        setOpen(false);
      }
    } catch (cause) {
      if (cause instanceof ArticleConflict && !controller.signal.aborted)
        setConflict(true);
      if (!controller.signal.aborted)
        setError(
          cause instanceof Error ? cause.message : "Could not save metadata.",
        );
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  return (
    <div className={styles.editor}>
      <button
        type="button"
        onClick={() => {
          setCategory(article.category ?? "OTHER");
          setName(article.entityName ?? "");
          setError("");
          setOpen(!open);
        }}
        disabled={busy}
        aria-expanded={open}
      >
        Edit details
      </button>
      {open && (
        <form onSubmit={(event) => void save(event)}>
          <label>
            Category
            <select
              value={category}
              disabled={busy}
              onChange={(event) => setCategory(event.target.value)}
            >
              {categories
                .filter((c) => c.enabled || c.key === category)
                .map((c) => (
                  <option key={c.key} value={c.key}>
                    {c.displayName}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Identified entity name
            <input
              value={name}
              disabled={busy}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <small>Changing this name does not change entity links.</small>
          {error && <p role="alert">{error}</p>}
          <div>
            <button disabled={busy || conflict} type="submit">
              {busy ? "Saving…" : "Save details"}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => setOpen(false)}
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
