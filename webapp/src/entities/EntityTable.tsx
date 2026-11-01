import type { EntitySummary } from "./model";
import { useId } from "react";
import { shortDate, statusLabel, types } from "./model";
import styles from "./Entities.module.css";
import { ViewingProgress } from "./ViewingProgress";
export function Status({
  entity,
}: {
  entity: Pick<EntitySummary, "type" | "status">;
}) {
  const config = types[entity.type];
  return (
    <span
      className={`${styles.status} ${entity.status === config.done ? styles.done : entity.status !== config.statuses[0] ? styles.progress : ""}`}
    >
      {statusLabel(entity.status)}
    </span>
  );
}
export function EntityTable({
  items,
  selected,
  onSelect,
  grouped = false,
}: {
  items: EntitySummary[];
  selected: string | null;
  onSelect: (id: string) => void;
  grouped?: boolean;
}) {
  if (!items.length) return null;
  const television = items[0].type === "TV_SHOW";
  return (
    <table className={`${styles.table} ${television ? styles.tvTable : ""}`}>
      <thead>
        <tr>
          <th scope="col">Title</th>
          {!grouped && <th scope="col">Status</th>}
          <th scope="col" className={styles.createdColumn}>
            Created
          </th>
          <th scope="col">
            {television ? "Progress" : types[items[0].type].date}
          </th>
        </tr>
      </thead>
      <tbody>
        {items.map((entity) => (
          <tr
            key={entity.id}
            className={entity.id === selected ? styles.selected : ""}
          >
            <td>
              <button
                className={styles.titleLink}
                onClick={() => onSelect(entity.id)}
              >
                {entity.title}
                {television && entity.tvShow?.premiereYear && (
                  <span className={styles.premiereYear}>
                    ({entity.tvShow.premiereYear})
                  </span>
                )}
                {entity.tvShow?.alias && ` · ${entity.tvShow.alias}`}
                {!television && (
                  <span className={styles.pencil} aria-hidden="true">
                    ✎
                  </span>
                )}
              </button>
              <small className={styles.mobileCreated}>
                Created {shortDate(entity.createdAt)}
              </small>
            </td>
            {!grouped && (
              <td>
                <Status entity={entity} />
              </td>
            )}
            <td className={`${styles.date} ${styles.createdColumn}`}>
              <time dateTime={entity.createdAt}>
                {shortDate(entity.createdAt)}
              </time>
            </td>
            <td className={styles.date}>
              {television ? (
                entity.tvShow ? (
                  <TvCompact entity={entity} />
                ) : (
                  <span className={styles.progressNote}>
                    Match a show to track episodes
                  </span>
                )
              ) : (
                <>
                  {entity.latestMatchingCompletionAt ? (
                    <time
                      dateTime={entity.latestMatchingCompletionAt}
                      title={`Completion timestamp (UTC): ${entity.latestMatchingCompletionAt}`}
                    >
                      {shortDate(entity.latestMatchingCompletionAt)}
                    </time>
                  ) : (
                    "—"
                  )}
                  {entity.completionCount > 0 && (
                    <small className={styles.count}>
                      {entity.completionCount}{" "}
                      {entity.completionCount === 1
                        ? "completion"
                        : "completions"}
                    </small>
                  )}
                </>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function TvCompact({ entity }: { entity: EntitySummary }) {
  const tooltipId = useId();
  if (!entity.tvShow) return null;
  return (
    <div className={styles.tvCompact}>
      <div className={styles.positionBadges}>
        {entity.tvShow.position ? (
          <>
            <span className={styles.seasonBadge}>
              {entity.tvShow.position.match(/^S\d+/)?.[0] ??
                `S${entity.tvShow.season ?? "?"}`}
            </span>
            <span className={styles.episodeBadge}>
              {entity.tvShow.position.match(/E[\d?]+$/)?.[0] ??
                `E${entity.tvShow.episode ?? "?"}`}
            </span>
            {(entity.tvShow.seasonEpisodeCount ?? 0) > 0 && (
              <span title="Episode number / episodes in this season">
                · {entity.tvShow.episode ?? "?"}/
                {entity.tvShow.seasonEpisodeCount}
              </span>
            )}
          </>
        ) : (
          <span className={styles.progressNote}>Position not set</span>
        )}
        {entity.tvShow.newEpisodesAvailable && (
          <span
            className={styles.newBadge}
            tabIndex={0}
            aria-describedby={tooltipId}
            aria-label="New episodes available"
          >
            NEW
            <span id={tooltipId} role="tooltip" className={styles.newTooltip}>
              New episodes available
            </span>
          </span>
        )}
      </div>
      <ViewingProgress
        title={entity.title}
        watchedCount={entity.tvShow.watchedCount ?? 0}
        airedCount={entity.tvShow.airedCount ?? 0}
        ready={entity.tvShow.catalogReady === true}
      />
    </div>
  );
}
