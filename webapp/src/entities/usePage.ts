import { useEffect, useRef, useState } from "react";
import type { Page } from "./api";
export function usePage<T extends { id: string }>(
  loader: (cursor: string | null, signal: AbortSignal) => Promise<Page<T>>,
) {
  const [items, setItems] = useState<T[]>([]),
    [cursor, setCursor] = useState<string | null>(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const request = useRef<AbortController | null>(null),
    last = useRef<string | null>(null);
  async function load(next: string | null) {
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
      if (!controller.signal.aborted) setLoading(false);
    }
  }
  useEffect(() => {
    setItems([]);
    setCursor(null);
    void load(null);
    return () => request.current?.abort();
  }, [loader]);
  return {
    items,
    cursor,
    loading,
    error,
    more: () => void load(cursor),
    retry: () => void load(last.current),
  };
}
