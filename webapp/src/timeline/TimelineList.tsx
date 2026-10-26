import { useEffect, useRef, useState, type CSSProperties } from "react";
import type { Summary } from "../api";
import { exactCaptureTime, timelineClock, timelineDate } from "./dates";
import styles from "./TimelineList.module.css";
export type TimelineItem = Summary & {
  categoryDisplay?: { key: string; displayName: string; color: string };
};
export function TimelineList({
  items,
  selected,
  onSelect,
  now = new Date(),
}: {
  items: TimelineItem[];
  selected: string | null;
  onSelect: (id: string) => void;
  now?: Date;
}) {
  const list = useRef<HTMLOListElement>(null);
  const [curve, setCurve] = useState("");
  useEffect(() => {
    const root = list.current;
    if (!root) return;
    const draw = () => {
      const box = root.getBoundingClientRect();
      const nodes = [...root.querySelectorAll("[data-timeline-node]")].map(
        (node) => {
          const r = node.getBoundingClientRect();
          return {
            x: r.left - box.left + r.width / 2,
            y: r.top - box.top + r.height / 2,
          };
        },
      );
      if (!nodes.length) {
        setCurve("");
        return;
      }
      let d = `M ${nodes[0].x} ${nodes[0].y - 24} Q ${nodes[0].x - 12} ${nodes[0].y - 12} ${nodes[0].x} ${nodes[0].y}`;
      for (let i = 1; i < nodes.length; i++) {
        const a = nodes[i - 1],
          b = nodes[i],
          mid = (a.y + b.y) / 2;
        d += ` C ${a.x + 14} ${mid} ${b.x - 14} ${mid} ${b.x} ${b.y}`;
      }
      const last = nodes[nodes.length - 1];
      d += ` Q ${last.x - 12} ${last.y + 20} ${last.x} ${last.y + 34}`;
      setCurve(d);
    };
    draw();
    window.addEventListener("resize", draw);
    const observer =
      typeof ResizeObserver === "undefined" ? null : new ResizeObserver(draw);
    observer?.observe(root);
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", draw);
    };
  }, [items]);
  return (
    <ol ref={list} className={styles.timeline} aria-label="Captured pages">
      <svg className={styles.spine} aria-hidden="true">
        <path d={curve} />
      </svg>
      {items.map((item, index) => {
        const color = item.categoryDisplay?.color || "var(--accent)";
        return (
          <li
            key={item.id}
            className={styles.row}
            style={
              {
                "--node-color": color,
                "--bend": `${[0, 6, 0, -5, 0, 4][index % 6]}px`,
              } as CSSProperties
            }
          >
            <time
              dateTime={item.savedAt}
              title={exactCaptureTime(item.savedAt)}
              className={styles.stamp}
            >
              <span>{timelineDate(item.savedAt, now)}</span>
              <strong>{timelineClock(item.savedAt)}</strong>
            </time>
            <span
              data-timeline-node
              className={`${styles.node} ${selected === item.id ? styles.activeNode : ""}`}
              aria-hidden="true"
            />
            <button
              className={styles.card}
              aria-pressed={selected === item.id}
              onClick={() => onSelect(item.id)}
            >
              {item.categoryDisplay && (
                <span className={styles.category}>
                  <i />
                  {item.categoryDisplay.displayName}
                </span>
              )}
              <strong>{item.entityName || item.title}</strong>
              <span className={styles.footer}>
                <span>
                  {new URL(item.url).hostname.replace(/^www\./, "")}
                  {item.hasContent ? "" : " · Link only"}
                </span>
                <span aria-hidden="true">→</span>
              </span>
            </button>
          </li>
        );
      })}
    </ol>
  );
}
