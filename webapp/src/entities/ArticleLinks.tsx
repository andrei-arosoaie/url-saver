import { useCallback, useEffect, useRef, useState } from "react";
import type { Article } from "../api";
import { requiresReload, type EntityApi } from "./api";
import { types, type EntityType, type LinkedEntity } from "./model";
import { usePage } from "./usePage";
import styles from "./ArticleLinks.module.css";

const entityTypeForCategory: Record<string, EntityType> = {
  BOOKS: "BOOK",
  MOVIES: "MOVIE",
  TV_SHOWS: "TV_SHOW",
  GAMES: "GAME",
};
// Metadata names do not define associations: existing links keep their entity IDs
// and remain explicitly removable even when the article's identified name changes.
export function useArticleLinks(article: Article | null, api: EntityApi) {
  const [revision, setRevision] = useState(0),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null),
    [notice, setNotice] = useState(""),
    [confirmed, setConfirmed] = useState<LinkedEntity | null>(null);
  const request = useRef<AbortController | null>(null);
  const load = useCallback(
    (cursor: string | null, signal: AbortSignal) =>
      article
        ? api.linked(article.id, cursor, signal)
        : Promise.resolve({ items: [] as LinkedEntity[], nextCursor: null }),
    [api, article?.id, revision],
  );
  const page = usePage(load);
  useEffect(() => {
    request.current?.abort();
    setError(null);
    setNotice("");
    setBusy(false);
    setConfirmed(null);
  }, [article?.id]);
  useEffect(() => () => request.current?.abort(), []);
  const type =
    article && Object.hasOwn(entityTypeForCategory, article.category)
      ? entityTypeForCategory[article.category]
      : undefined;
  const items =
    confirmed && !page.items.some((e) => e.id === confirmed.id)
      ? [confirmed, ...page.items]
      : page.items;
  const target =
    type && article?.entityName
      ? items.find((e) => e.type === type && e.title === article.entityName)
      : undefined;
  const canAdd = Boolean(
    type && article?.entityName && !items.length && !page.cursor && !page.error,
  );
  async function mutate(
    action: (signal: AbortSignal) => Promise<unknown>,
    added = false,
  ) {
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(null);
    try {
      const result = await action(controller.signal);
      if (!controller.signal.aborted) {
        if (
          added &&
          result &&
          typeof result === "object" &&
          "entity" in result &&
          "link" in result
        ) {
          const response = result as Awaited<
            ReturnType<EntityApi["addFromArticle"]>
          >;
          setConfirmed({
            ...response.entity,
            linkedAt: response.link.linkedAt,
          });
        } else setConfirmed(null);
        setNotice(
          added ? "Entity added." : "Page unlinked. Entity and history kept.",
        );
        setRevision((n) => n + 1);
      }
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  function remove(entity: LinkedEntity) {
    if (article)
      void mutate((signal) => api.unlink(article.id, entity.id, signal));
  }
  function add() {
    if (article && type && article.entityName)
      void mutate(
        (signal) =>
          api.addFromArticle(article.id, type, article.entityName!, signal),
        true,
      );
  }
  return {
    revision,
    article,
    page,
    items,
    target,
    canAdd,
    busy,
    error,
    notice,
    blocked: busy || page.loading || requiresReload(error),
    add,
    remove,
    reload() {
      setError(null);
      setConfirmed(null);
      setRevision((n) => n + 1);
    },
  };
}
type LinkState = ReturnType<typeof useArticleLinks>;
function ListIcon({ remove = false }: { remove?: boolean }) {
  return (
    <svg
      viewBox="0 0 28 28"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M8 6h14M8 13h14M8 20h7" />
      <circle cx="3" cy="6" r=".7" />
      <circle cx="3" cy="13" r=".7" />
      <circle cx="3" cy="20" r=".7" />
      <path d="M18 21h8" />
      {!remove && <path d="M22 17v8" />}
    </svg>
  );
}
export function ArticleLinkButton({ state }: { state: LinkState }) {
  if (state.target)
    return (
      <button
        className={styles.button}
        disabled={state.blocked}
        aria-label={`Remove ${state.target.title} from this page`}
        onClick={() => state.remove(state.target!)}
      >
        <ListIcon remove />
        <span>{state.busy ? "Saving…" : "Remove"}</span>
      </button>
    );
  if (state.canAdd)
    return (
      <button
        className={styles.button}
        disabled={state.blocked}
        aria-label={`Add ${state.article?.entityName} to ${types[entityTypeForCategory[state.article!.category]].label}`}
        onClick={state.add}
      >
        <ListIcon />
        <span>{state.busy ? "Saving…" : "Add"}</span>
      </button>
    );
  return (
    <span className={styles.kicker}>
      {state.page.loading ? "Checking links…" : "Entry details"}
    </span>
  );
}
export function ArticleLinkedEntities({
  state,
  onOpen,
}: {
  state: LinkState;
  onOpen: (entity: LinkedEntity) => void;
}) {
  if (!state.items.length && !state.error && !state.page.error && !state.notice)
    return null;
  return (
    <section className={styles.links} aria-label="Linked entities">
      {state.items.length > 0 && (
        <>
          <h3>Linked entities</h3>
          <ul>
            {state.items.map((entity) => (
              <li key={entity.id}>
                <button className={styles.open} onClick={() => onOpen(entity)}>
                  {entity.title} ↗
                </button>
                {state.target?.id !== entity.id && (
                  <button
                    className={styles.remove}
                    disabled={state.blocked}
                    aria-label={`Unlink ${entity.title}`}
                    onClick={() => state.remove(entity)}
                  >
                    Remove
                  </button>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
      {state.page.cursor && (
        <button
          className={styles.remove}
          disabled={state.page.loading}
          onClick={state.page.more}
        >
          Load more links
        </button>
      )}
      {state.notice && <p role="status">{state.notice}</p>}
      {(state.error instanceof Error || state.page.error) && (
        <div role="alert">
          {state.error instanceof Error
            ? state.error.message
            : state.page.error}
          <button className={styles.remove} onClick={state.reload}>
            Reload links
          </button>
        </div>
      )}
    </section>
  );
}
