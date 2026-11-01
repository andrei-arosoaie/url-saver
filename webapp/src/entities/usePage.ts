import { useEffect, useRef, useState } from "react";
import type { Page } from "./api";
export function usePage<T extends { id: string }>(
  loader: (cursor: string | null, signal: AbortSignal) => Promise<Page<T>>,
  refreshInterval = 0,
) {
  const [items, setItems] = useState<T[]>([]),
    [cursor, setCursor] = useState<string | null>(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const request = useRef<AbortController | null>(null),
    last = useRef<string | null>(null);
  const currentItems = useRef(items),
    busy = useRef(false),
    generation = useRef(0),
    refreshRequest = useRef<AbortController | null>(null);
  currentItems.current = items;
  async function load(next: string | null) {
    generation.current++;
    refreshRequest.current?.abort();
    busy.current = true;
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    last.current = next;
    setLoading(true);
    setError("");
    try {
      const page = await loader(next, controller.signal);
      if (!controller.signal.aborted) {
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
      }
    } catch (cause) {
      if (!controller.signal.aborted)
        setError(
          cause instanceof Error ? cause.message : "Could not load records.",
        );
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false);
        busy.current = false;
      }
    }
  }
  useEffect(() => {
    setItems([]);
    setCursor(null);
    void load(null);
    return () => request.current?.abort();
  }, [loader]);
  useEffect(() => {
    if (!refreshInterval) return;
    let active = false;
    const refresh = async () => {
      if (active || busy.current || document.visibilityState !== "visible")
        return;
      active = true;
      const controller = new AbortController();
      refreshRequest.current = controller;
      const snapshot = generation.current,
        target = Math.max(1, currentItems.current.length);
      const fresh = new Map<string, T>();
      let next: string | null = null;
      try {
        do {
          const page = await loader(next, controller.signal);
          for (const item of page.items) fresh.set(item.id, item);
          if (page.nextCursor === next && next !== null)
            throw new Error("Repeated page cursor");
          next = page.nextCursor;
        } while (next && fresh.size < target && !controller.signal.aborted);
        if (!controller.signal.aborted && snapshot === generation.current) {
          setItems([...fresh.values()]);
          setCursor(next);
        }
      } catch {
        /* Keep the current list during a transient background refresh failure. */
      } finally {
        active = false;
      }
    };
    const visible = () => {
      void refresh();
    };
    const timer = setInterval(visible, refreshInterval);
    window.addEventListener("focus", visible);
    document.addEventListener("visibilitychange", visible);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", visible);
      document.removeEventListener("visibilitychange", visible);
      refreshRequest.current?.abort();
    };
  }, [loader, refreshInterval]);
  return {
    items,
    cursor,
    loading,
    error,
    more: () => void load(cursor),
    retry: () => void load(last.current),
  };
}
