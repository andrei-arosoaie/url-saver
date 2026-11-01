import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
} from "react";
import { entityApi, requiresReload, type EntityApi } from "./api";
import {
  types,
  statusLabel,
  shortDate,
  type EntityRoute,
  type EntitySummary,
  type EntityType,
  type CompletionYear,
} from "./model";
import { usePage } from "./usePage";
import { EntityTable, TvCompact } from "./EntityTable";
import { EntityDetails } from "./EntityDetails";
import { ManualEntity } from "./ManualEntity";
import styles from "./Entities.module.css";

export function EntitiesPage({
  route,
  navigate,
  onOpenArticle,
  api = entityApi,
}: {
  route: EntityRoute;
  navigate: (route: EntityRoute, replace?: boolean) => void;
  onOpenArticle: (id: string) => void;
  api?: EntityApi;
}) {
  const [revision, setRevision] = useState(0),
    [manual, setManual] = useState(false),
    [years, setYears] = useState<CompletionYear[]>([]),
    [yearsError, setYearsError] = useState(""),
    [yearsLoading, setYearsLoading] = useState(true),
    [yearRetry, setYearRetry] = useState(0),
    [busyId, setBusyId] = useState<string | null>(null),
    [writeError, setWriteError] = useState<unknown>(null),
    [notice, setNotice] = useState("");
  const write = useRef<AbortController | null>(null);
  const config = types[route.type];
  const load = useCallback(
    (cursor: string | null, signal: AbortSignal) =>
      route.view === "year"
        ? Promise.resolve({ items: [] as EntitySummary[], nextCursor: null })
        : api.list(
            {
              type: route.type,
              query: route.query,
              completionYear: route.year ? Number(route.year) : undefined,
            },
            cursor,
            signal,
          ),
    [api, route.type, route.query, route.year, route.view, revision],
  );
  const page = usePage(load);
  useEffect(() => {
    const controller = new AbortController();
    setYears([]);
    setYearsLoading(true);
    setYearsError("");
    void api
      .years(route.type, route.query, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setYears(value);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setYearsError(
            cause instanceof Error
              ? cause.message
              : "Could not load completion years.",
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setYearsLoading(false);
      });
    return () => controller.abort();
  }, [api, route.type, route.query, revision, yearRetry]);
  useEffect(() => {
    setWriteError(null);
    setNotice("");
  }, [route.type, route.view]);
  useEffect(() => () => write.current?.abort(), []);
  useEffect(() => {
    document.title = `${config.label} · Know`;
  }, [config.label]);
  function changed() {
    setRevision((n) => n + 1);
    setNotice("Changes saved.");
  }
  function open(id: string) {
    navigate({ ...route, id });
  }
  async function boardStatus(entity: EntitySummary, status: string) {
    const controller = new AbortController();
    write.current = controller;
    setBusyId(entity.id);
    setWriteError(null);
    try {
      await api.update(entity, { status }, controller.signal);
      if (!controller.signal.aborted) changed();
    } catch (cause) {
      if (!controller.signal.aborted) setWriteError(cause);
    } finally {
      if (!controller.signal.aborted) setBusyId(null);
    }
  }
  const selected = route.id ?? page.items[0]?.id ?? null;
  const yearOptions = [
    ...new Set([
      ...years.map((y) => String(y.year)),
      ...(route.year ? [route.year] : []),
    ]),
  ].sort((a, b) => Number(b) - Number(a));
  return (
    <main
      id="main-content"
      tabIndex={-1}
      className={`${styles.main} ${route.id ? styles.hasSelection : ""}`}
    >
      <div className={styles.heading}>
        <h1>{config.label}</h1>
        <button className={styles.action} onClick={() => setManual(true)}>
          ＋ Add {config.singular}
        </button>
      </div>
      <div className={styles.typeTabs} role="group" aria-label="Entity type">
        {(Object.keys(types) as EntityType[]).map((type) => (
          <button
            key={type}
            aria-pressed={route.type === type}
            onClick={() =>
              navigate({ ...route, type, id: null, query: "", year: "" })
            }
          >
            {types[type].label}
          </button>
        ))}
      </div>
      <div className={styles.toolbar}>
        <div className={styles.viewTabs} role="group" aria-label="Entity view">
          {(["table", "board", "year"] as const).map((view) => (
            <button
              key={view}
              aria-pressed={route.view === view}
              onClick={() => navigate({ ...route, view, id: null })}
            >
              {view === "year"
                ? "By year"
                : view === "table"
                  ? "Table"
                  : "Board"}
            </button>
          ))}
        </div>
        <label className={styles.yearControl}>
          {config.year}
          <select
            aria-label="Completion year"
            value={route.year}
            onChange={(event) =>
              navigate({ ...route, year: event.target.value, id: null }, true)
            }
          >
            <option value="">All years</option>
            {yearOptions.map((year) => (
              <option key={year}>{year}</option>
            ))}
          </select>
        </label>
      </div>
      {writeError instanceof Error && (
        <div role="alert" className={styles.error}>
          {writeError.message}
          {requiresReload(writeError) && (
            <button
              className={styles.action}
              onClick={() => {
                setWriteError(null);
                setRevision((n) => n + 1);
              }}
            >
              Reload entities
            </button>
          )}
        </div>
      )}
      <div className={styles.workspace}>
        <div className={styles.collection}>
          {route.view === "year" ? (
            <>
              {yearsLoading && <p role="status">Loading completion years…</p>}
              {yearsError && (
                <p role="alert">
                  {yearsError}
                  <button
                    className={styles.action}
                    onClick={() => setYearRetry((n) => n + 1)}
                  >
                    Try again
                  </button>
                </p>
              )}
              {!yearsLoading &&
                !yearsError &&
                !years.some(
                  (y) => !route.year || String(y.year) === route.year,
                ) && (
                  <p className={styles.empty}>
                    No completed {config.label.toLowerCase()} match your
                    filters.
                  </p>
                )}
              {years
                .filter((y) => !route.year || String(y.year) === route.year)
                .map((year) => (
                  <YearGroup
                    key={`${route.type}:${year.year}:${revision}`}
                    year={year}
                    route={route}
                    api={api}
                    onSelect={open}
                    selected={selected}
                  />
                ))}
            </>
          ) : (
            <>
              {page.error && (
                <p role="alert" className={styles.error}>
                  {page.error}
                  <button className={styles.action} onClick={page.retry}>
                    Try again
                  </button>
                </p>
              )}
              {route.view === "table" ? (
                <EntityTable
                  items={page.items}
                  selected={selected}
                  onSelect={open}
                />
              ) : (
                <div
                  className={styles.board}
                  style={
                    { "--columns": config.statuses.length } as CSSProperties
                  }
                >
                  {config.statuses.map((status) => {
                    const cards = page.items.filter((e) => e.status === status);
                    return (
                      <section key={status} className={styles.boardColumn}>
                        <h3>
                          {statusLabel(status)}{" "}
                          <small>
                            {cards.length}
                            {page.cursor ? " loaded" : ""}
                          </small>
                        </h3>
                        {cards.map((entity) => (
                          <article
                            key={entity.id}
                            className={`${styles.boardCard} ${selected === entity.id ? styles.selectedCard : ""}`}
                          >
                            <button
                              className={styles.titleLink}
                              onClick={() => open(entity.id)}
                            >
                              {entity.title}
                              {entity.tvShow?.alias &&
                                ` ? ${entity.tvShow.alias}`}
                              <span
                                className={styles.pencil}
                                aria-hidden="true"
                              >
                                ✎
                              </span>
                            </button>
                            <TvCompact entity={entity} />
                            <p>Created: {shortDate(entity.createdAt)}</p>
                            {entity.latestMatchingCompletionAt && (
                              <p>
                                {config.date}:{" "}
                                {shortDate(entity.latestMatchingCompletionAt)}
                              </p>
                            )}
                            <select
                              aria-label={`Status for ${entity.title}`}
                              value={entity.status}
                              disabled={
                                busyId !== null || requiresReload(writeError)
                              }
                              onChange={(event) =>
                                void boardStatus(entity, event.target.value)
                              }
                            >
                              {config.statuses.map((s) => (
                                <option key={s} value={s}>
                                  {statusLabel(s)}
                                </option>
                              ))}
                            </select>
                          </article>
                        ))}
                      </section>
                    );
                  })}
                </div>
              )}
              {page.loading && <p role="status">Loading entities…</p>}
              {!page.loading &&
                !page.error &&
                !page.items.length &&
                route.view === "table" && (
                  <p className={styles.empty}>
                    No {config.label.toLowerCase()} match your filters.
                  </p>
                )}
              {page.cursor && !page.error && (
                <button
                  className={styles.action}
                  onClick={page.more}
                  disabled={page.loading}
                >
                  Load more entities
                </button>
              )}
            </>
          )}
        </div>
        {selected ? (
          <EntityDetails
            key={selected}
            id={selected}
            focus={Boolean(route.id)}
            api={api}
            revision={revision}
            onChanged={changed}
            onBack={() => navigate({ ...route, id: null })}
            onOpenArticle={onOpenArticle}
          />
        ) : (
          <aside className={styles.detail}>
            <p className={styles.help}>Select an entity for details.</p>
          </aside>
        )}
      </div>
      <p className={styles.help}>
        {route.view === "year"
          ? "Grouped by completion year in UTC. Unfinished entries stay in Table and Board."
          : route.year
            ? `Completion history in ${route.year} (UTC). Creation dates are separate.`
            : ""}
      </p>
      <p role="status" className={styles.notice}>
        {notice}
      </p>
      {manual && (
        <ManualEntity
          type={route.type}
          api={api}
          onClose={() => setManual(false)}
          onExisting={(id) => {
            setManual(false);
            open(id);
          }}
          onCreated={(entity) => {
            setManual(false);
            setRevision((n) => n + 1);
            navigate({
              ...route,
              view: "table",
              id: entity.id,
              query: "",
              year: "",
            });
            setNotice(`${config.singular} added.`);
          }}
        />
      )}
    </main>
  );
}
function YearGroup({
  year,
  route,
  api,
  selected,
  onSelect,
}: {
  year: CompletionYear;
  route: EntityRoute;
  api: EntityApi;
  selected: string | null;
  onSelect: (id: string) => void;
}) {
  const load = useCallback(
    (cursor: string | null, signal: AbortSignal) =>
      api.list(
        { type: route.type, query: route.query, completionYear: year.year },
        cursor,
        signal,
      ),
    [api, route.type, route.query, year.year],
  );
  const page = usePage(load);
  return (
    <details className={styles.yearGroup} open>
      <summary>
        <strong>{year.year}</strong>{" "}
        <small>
          · {year.entityCount} {year.entityCount === 1 ? "entity" : "entities"}{" "}
          · {year.completionCount}{" "}
          {year.completionCount === 1 ? "completion" : "completions"}
        </small>
      </summary>
      <EntityTable
        items={page.items}
        selected={selected}
        onSelect={onSelect}
        grouped
      />
      {page.loading && <p role="status">Loading {year.year}…</p>}
      {page.error && (
        <p role="alert">
          {page.error}
          <button className={styles.action} onClick={page.retry}>
            Try again
          </button>
        </p>
      )}
      {page.cursor && !page.error && (
        <button
          className={styles.action}
          disabled={page.loading}
          onClick={page.more}
        >
          Load more from {year.year}
        </button>
      )}
    </details>
  );
}
