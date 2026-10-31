import { useEffect, useRef, useState } from "react";
import {
  libraryApi,
  type Article,
  type LibraryApi,
  type Summary,
  type Category,
} from "./api";
import { Brand, TimelineIcon } from "./Brand";
import { exactCaptureTime } from "./timeline/dates";
import { ArticleBody } from "./ArticleBody";
import { ScreenshotPreview } from "./screenshots/ScreenshotPreview";
import styles from "./App.module.css";
import { TimelineList } from "./timeline/TimelineList";
import { MetadataEditor } from "./timeline/MetadataEditor";
import { ThemePicker } from "./theme/ThemePicker";
import { useTheme } from "./theme/useTheme";
import { EntitiesPage } from "./entities/EntitiesPage";
import { CategoriesPage } from "./categories/CategoriesPage";
import { categoryApi, type CategoryApi } from "./categories/api";
import { DeleteArticle } from "./timeline/DeleteArticle";
import { IdentificationPanel } from "./identification/IdentificationPanel";
import {
  useArticleLinks,
  ArticleLinkButton,
  ArticleLinkedEntities,
} from "./entities/ArticleLinks";
import { entityApi, type EntityApi } from "./entities/api";
import {
  entityRoute,
  entityHash,
  types,
  type EntityRoute,
} from "./entities/model";
function selectedFromUrl() {
  const match = location.hash.match(/^#article\/([0-9a-f-]{36})$/i);
  return match?.[1] ?? null;
}
function domain(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}
export function App({
  api = libraryApi,
  entities = entityApi,
  categoryManagement = categoryApi,
}: {
  api?: LibraryApi;
  entities?: EntityApi;
  categoryManagement?: CategoryApi;
}) {
  const theme = useTheme();
  const [hash, setHash] = useState(() => location.hash);
  const isEntities = hash.startsWith("#entities");
  const isCategories = hash === "#categories";
  const route = entityRoute(hash);
  const [categories, setCategories] = useState<Category[]>([]);
  const [category, setCategory] = useState("");
  const [query, setQuery] = useState("");

  const [catalogError, setCatalogError] = useState("");
  const [catalogRevision, setCatalogRevision] = useState(0);
  const [items, setItems] = useState<Summary[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState("");
  const [listRevision, setListRevision] = useState(0);
  const [selected, setSelected] = useState<string | null>(selectedFromUrl);
  const [article, setArticle] = useState<Article | null>(null);
  const [reading, setReading] = useState(false);
  const [readError, setReadError] = useState("");
  const [retry, setRetry] = useState(0);
  const listRequest = useRef<AbortController | null>(null);
  const catalogRequest = useRef<AbortController | null>(null);
  const lastListCursor = useRef<string | null>(null);
  const reader = useRef<HTMLElement>(null);
  async function load(next: string | null) {
    lastListCursor.current = next;
    listRequest.current?.abort();
    const controller = new AbortController();
    listRequest.current = controller;
    setLoading(true);
    setListError("");
    try {
      const page = await api.list(next, controller.signal, {
        category: category || undefined,
        query: query || undefined,
      });
      if (controller.signal.aborted) return;
      setItems((previous) =>
        next
          ? [
              ...new Map(
                [...previous, ...page.items].map((item) => [item.id, item]),
              ).values(),
            ]
          : page.items,
      );
      setCursor(page.nextCursor);
    } catch (error) {
      if (!controller.signal.aborted)
        setListError(
          error instanceof Error
            ? error.message
            : "Could not load your articles.",
        );
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }
  useEffect(() => {
    setItems([]);
    setCursor(null);
    void load(null);
    return () => listRequest.current?.abort();
  }, [api, category, query, listRevision]);
  useEffect(() => {
    const controller = new AbortController();
    catalogRequest.current = controller;
    setCatalogError("");
    if (api.categories)
      void api
        .categories(controller.signal)
        .then((value) => {
          if (!controller.signal.aborted) setCategories(value);
        })
        .catch(() => {
          if (!controller.signal.aborted)
            setCatalogError("Categories unavailable. Refresh to try again.");
        });
    return () => controller.abort();
  }, [api, catalogRevision]);
  useEffect(() => {
    if (!selected && !isEntities)
      document.title = isCategories
        ? "Categories · Know"
        : "Know · Saved articles";
  }, [hash, selected]);
  useEffect(() => {
    const changed = () => {
      setHash(location.hash);
      setSelected(selectedFromUrl());
    };
    window.addEventListener("popstate", changed);
    window.addEventListener("hashchange", changed);
    return () => {
      window.removeEventListener("popstate", changed);
      window.removeEventListener("hashchange", changed);
    };
  }, []);
  useEffect(() => {
    setArticle(null);
    setReadError("");
    if (!isEntities && !isCategories) document.title = "Know · Saved articles";
    if (!selected) {
      setReading(false);
      document
        .getElementById("library-heading")
        ?.focus({ preventScroll: true });
      return;
    }
    const controller = new AbortController();
    setReading(true);
    reader.current?.focus();
    void api
      .read(selected, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) {
          setArticle(value);
          document.title = `${value.title} · Know`;
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setReadError(
            error instanceof Error
              ? error.message
              : "Could not load this article.",
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setReading(false);
      });
    return () => controller.abort();
  }, [selected, retry, api]);
  function navigate(id: string | null) {
    if (isCategories) setListRevision((v) => v + 1);
    history.pushState(
      null,
      "",
      id ? `#article/${id}` : `${location.pathname}${location.search}`,
    );
    setSelected(id);
    setHash(id ? `#article/${id}` : "");
    if (!id) {
      document.title = "Know · Saved articles";
      document.getElementById("library-heading")?.focus();
    }
  }
  function navigateEntity(next: EntityRoute, replace = false) {
    const value = entityHash(next);
    history[replace ? "replaceState" : "pushState"](null, "", value);
    setHash(value);
    setSelected(null);
  }
  function navigateCategories() {
    history.pushState(null, "", "#categories");
    setHash("#categories");
    setSelected(null);
  }
  const currentCategory = categories.find((c) => c.key === article?.category);
  const articleLinks = useArticleLinks(article, entities);
  return (
    <div className={styles.app}>
      <a
        className={styles.skip}
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main-content")?.focus();
        }}
      >
        Skip to content
      </a>
      <header className={styles.header}>
        <a
          href="#"
          className={styles.brand}
          onClick={(event) => {
            event.preventDefault();
            navigate(null);
          }}
        >
          <Brand />
        </a>
        {!isCategories && (
          <label className={styles.search}>
            <span className={styles.srOnly}>
              {isEntities
                ? `Search ${types[route.type].label.toLowerCase()}`
                : "Search saved entries"}
            </span>
            <input
              type="search"
              value={isEntities ? route.query : query}
              placeholder={
                isEntities
                  ? `Search ${types[route.type].label.toLowerCase()}…`
                  : "Search saved entries…"
              }
              autoComplete="off"
              onChange={(event) =>
                isEntities
                  ? navigateEntity(
                      { ...route, query: event.target.value, id: null },
                      true,
                    )
                  : setQuery(event.target.value)
              }
            />
          </label>
        )}
        <ThemePicker
          preference={theme.preference}
          onChange={theme.setPreference}
        />
        <span
          className={styles.avatar}
          title="Shared temporary account"
          aria-label="Shared library"
        >
          S
        </span>
      </header>
      <div className={styles.shell}>
        <nav className={styles.primaryNav} aria-label="Main navigation">
          <button
            className={styles.navItem}
            aria-current={isEntities || isCategories ? undefined : "page"}
            onClick={() => navigate(null)}
          >
            <TimelineIcon />
            Timeline
          </button>
          <button
            className={styles.navItem}
            aria-current={isEntities ? "page" : undefined}
            onClick={() => {
              if (!isEntities) navigateEntity(entityRoute("#entities"));
            }}
          >
            <span className={styles.entitiesIcon} aria-hidden="true">
              ☷
            </span>
            Entities
          </button>
          <button
            className={styles.navItem}
            aria-current={isCategories ? "page" : undefined}
            onClick={() => {
              if (!isCategories) navigateCategories();
            }}
          >
            <span className={styles.entitiesIcon} aria-hidden="true">
              ▦
            </span>
            Categories
          </button>
        </nav>
        {isCategories ? (
          <CategoriesPage
            api={categoryManagement}
            onChanged={(rows) => {
              catalogRequest.current?.abort();
              setCategories(rows);
              if (
                category &&
                !rows.some((c) => c.key === category && c.enabled)
              )
                setCategory("");
            }}
            onArticles={(key) => {
              setCategory(key);
              setQuery("");
              navigate(null);
            }}
          />
        ) : isEntities ? (
          <EntitiesPage
            route={route}
            api={entities}
            navigate={navigateEntity}
            onOpenArticle={navigate}
          />
        ) : (
          <main
            id="main-content"
            tabIndex={-1}
            className={`${styles.layout} ${selected ? styles.hasSelection : ""}`}
          >
            <aside className={styles.sidebar} aria-label="Saved articles">
              <div className={styles.libraryTitle}>
                <div>
                  <h1 id="library-heading" tabIndex={-1}>
                    Saved entries
                  </h1>
                  <p>
                    {items.length} {items.length === 1 ? "entry" : "entries"}
                    {cursor ? " loaded" : ""} · Newest first
                  </p>
                </div>
                <button
                  className={styles.refresh}
                  aria-label="Refresh articles"
                  disabled={loading}
                  onClick={() => void load(null)}
                >
                  ↻
                </button>
              </div>
              {categories.length > 0 && (
                <fieldset className={styles.filters}>
                  <legend>Filter by category</legend>
                  <div>
                    <button
                      type="button"
                      aria-pressed={category === ""}
                      onClick={() => setCategory("")}
                    >
                      All categories
                    </button>
                    {categories
                      .filter((c) => c.enabled)
                      .sort((a, b) => {
                        const order = [
                          "ARCHITECTURE",
                          "DEVELOPMENT",
                          "AI",
                          "OTHER",
                          "BOOKS",
                          "MOVIES",
                          "TV_SHOWS",
                          "GAMES",
                        ];
                        return (
                          (order.indexOf(a.key) < 0
                            ? 99
                            : order.indexOf(a.key)) -
                          (order.indexOf(b.key) < 0 ? 99 : order.indexOf(b.key))
                        );
                      })
                      .map((c) => (
                        <button
                          type="button"
                          key={c.key}
                          aria-pressed={category === c.key}
                          onClick={() => setCategory(c.key)}
                        >
                          <i style={{ background: c.color }} />
                          {c.displayName}
                        </button>
                      ))}
                  </div>
                </fieldset>
              )}
              {catalogError && (
                <p role="status" className={styles.notice}>
                  {catalogError}
                </p>
              )}
              {!items.length && !loading && !listError && (
                <p className={styles.notice}>
                  {category || query
                    ? "No entries match your search and category."
                    : "No saved entries yet. Save a page with the Know Firefox extension."}
                </p>
              )}
              <TimelineList
                items={items.map((item) => ({
                  ...item,
                  categoryDisplay: categories.find(
                    (c) => c.key === item.category,
                  ),
                }))}
                selected={selected}
                onSelect={navigate}
              />
              {loading && (
                <p role="status" className={styles.notice}>
                  Loading articles…
                </p>
              )}
              {listError && (
                <div role="alert" className={styles.error}>
                  <p>{listError}</p>
                  <button onClick={() => void load(lastListCursor.current)}>
                    Try again
                  </button>
                </div>
              )}
              {!listError && cursor && (
                <button
                  className={styles.loadMore}
                  disabled={loading}
                  onClick={() => void load(cursor)}
                >
                  Load more
                </button>
              )}
              {!loading && !listError && !cursor && items.length > 0 && (
                <p className={styles.endMarker}>
                  ● &nbsp; Beginning of this timeline
                </p>
              )}
            </aside>
            <section
              ref={reader}
              className={styles.reader}
              tabIndex={-1}
              aria-label="Article reader"
            >
              {selected ? (
                <>
                  <div className={styles.readerBar}>
                    <button
                      className={styles.back}
                      onClick={() => navigate(null)}
                    >
                      ← Timeline
                    </button>
                    {article ? (
                      <ArticleLinkButton state={articleLinks} />
                    ) : (
                      <span className={styles.readerKicker}>Entry details</span>
                    )}
                  </div>
                  {reading && <p role="status">Opening article…</p>}
                  {readError && (
                    <div role="alert" className={styles.error}>
                      <h2>Couldn’t open this article</h2>
                      <p>{readError}</p>
                      <button onClick={() => setRetry((v) => v + 1)}>
                        Try again
                      </button>
                    </div>
                  )}
                  {article && (
                    <article className={styles.article}>
                      <h2>{article.title}</h2>
                      <dl className={styles.facts}>
                        <dt>Created</dt>
                        <dd>
                          <time dateTime={article.savedAt}>
                            {exactCaptureTime(article.savedAt)}
                          </time>
                        </dd>
                        <dt>Category</dt>
                        <dd>
                          {currentCategory && (
                            <i style={{ background: currentCategory.color }} />
                          )}
                          {currentCategory?.displayName ?? article.category}
                        </dd>
                        {article.entityName && (
                          <>
                            <dt>Name</dt>
                            <dd className={styles.entityName}>
                              {article.entityName}
                            </dd>
                          </>
                        )}
                        <dt>Source</dt>
                        <dd>{domain(article.url)}</dd>
                        <dt>Original URL</dt>
                        <dd>
                          <a
                            href={article.url}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            Open original page ↗
                          </a>
                        </dd>
                        <dt>Content</dt>
                        <dd>
                          {article.content ? "Saved content" : "Link only"}
                        </dd>
                      </dl>
                      {api.identification && (
                        <IdentificationPanel
                          key={`identification-${article.id}`}
                          article={article}
                          api={api.identification}
                          onFinished={async (signal) => {
                            const updated = await api.read(article.id, signal);
                            if (signal.aborted) return;
                            setArticle((previous) =>
                              previous?.id === updated.id ? updated : previous,
                            );
                            setItems((previous) =>
                              previous.map((item) =>
                                item.id === updated.id
                                  ? {
                                      ...item,
                                      category: updated.category,
                                      entityName: updated.entityName,
                                      version: updated.version,
                                    }
                                  : item,
                              ),
                            );
                            setCatalogRevision((v) => v + 1);
                            if (category || query) void load(null);
                          }}
                        />
                      )}
                      <MetadataEditor
                        key={`metadata-${article.id}`}
                        article={article}
                        categories={categories}
                        api={api}
                        onSaved={(updated) => {
                          setArticle(updated);
                          setCatalogRevision((v) => v + 1);
                          void load(null);
                        }}
                      />
                      {api.deleteArticle && (
                        <div className={styles.deleteAction}>
                          <DeleteArticle
                            article={article}
                            remove={api.deleteArticle}
                            onDeleted={() => {
                              setArticle(null);
                              setItems((previous) =>
                                previous.filter(
                                  (item) => item.id !== article.id,
                                ),
                              );
                              navigate(null);
                              setCatalogRevision((v) => v + 1);
                              void load(null);
                            }}
                            onReload={() => {
                              setRetry((v) => v + 1);
                              void load(null);
                            }}
                          />
                        </div>
                      )}
                      <ArticleLinkedEntities
                        state={articleLinks}
                        onOpen={(entity) =>
                          navigateEntity({
                            ...entityRoute("#entities"),
                            type: entity.type,
                            id: entity.id,
                          })
                        }
                      />
                      <ScreenshotPreview
                        key={`screenshot-${article.id}`}
                        articleId={article.id}
                      />
                      {article.content ? (
                        <ArticleBody
                          html={article.content}
                          theme={theme.resolved}
                        />
                      ) : (
                        <div className={styles.linkOnly}>
                          <strong>Link only</strong>
                          <p>
                            No content was saved. You can still open the
                            original page.
                          </p>
                        </div>
                      )}
                    </article>
                  )}
                </>
              ) : (
                <p className={styles.selection}>Select an entry for details.</p>
              )}
            </section>
          </main>
        )}
      </div>
    </div>
  );
}
