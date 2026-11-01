import styles from "./Entities.module.css";

export function ViewingProgress({
  watchedCount,
  airedCount,
  ready = true,
  title,
}: {
  watchedCount: number;
  airedCount: number;
  ready?: boolean;
  title: string;
}) {
  if (!ready)
    return (
      <span className={styles.progressNote}>Updating episode catalog…</span>
    );
  const total = Math.max(0, airedCount),
    watched = Math.min(total, Math.max(0, watchedCount));
  const percent =
    total > 0
      ? watched === total
        ? 100
        : Math.floor((watched * 100) / total)
      : 0;
  const remaining = total - watched;
  return (
    <div
      className={`${styles.viewingProgress} ${total > 0 && !remaining ? styles.caughtUp : ""}`}
    >
      <div className={styles.progressNumbers}>
        <span>
          {watched} / {total} released episodes watched
        </span>
        <strong>{percent}%</strong>
      </div>
      <progress
        aria-label={`Viewing progress for ${title}`}
        aria-valuetext={`${watched} of ${total} released episodes watched; ${remaining} remaining`}
        value={watched}
        max={total || 1}
      />
      <span className={styles.progressNote}>
        {!total
          ? "No released episodes yet"
          : remaining
            ? `${remaining} ${remaining === 1 ? "episode" : "episodes"} left to catch up`
            : "Caught up with all released episodes"}
      </span>
    </div>
  );
}
