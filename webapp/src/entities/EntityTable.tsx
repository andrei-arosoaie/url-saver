import type { EntitySummary } from "./model";
import { shortDate, statusLabel, types } from "./model";
import styles from "./Entities.module.css";
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
  return (
    <table className={styles.table}>
      <thead>
        <tr>
          <th scope="col">Title</th>
          {!grouped && <th scope="col">Status</th>}
          <th scope="col" className={styles.createdColumn}>
            Created
          </th>
          <th scope="col">{types[items[0].type].date}</th>
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
                <span className={styles.pencil} aria-hidden="true">
                  ✎
                </span>
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
                  {entity.completionCount === 1 ? "completion" : "completions"}
                </small>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
