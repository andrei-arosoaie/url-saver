import { useEffect, useId, useState } from "react";
import { tvRequest, type Candidate, type Search } from "./tvApi";
import styles from "./Entities.module.css";

export function TvAutocomplete({
  value,
  onChange,
  selected,
  onSelect,
  disabled = false,
}: {
  value: string;
  onChange: (value: string) => void;
  selected: Candidate | null;
  onSelect: (candidate: Candidate | null) => void;
  disabled?: boolean;
}) {
  const id = useId();
  const [settings, setSettings] = useState<{
    minimumCharacters: number;
    debounceMs: number;
    pageSize: number;
  } | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    void tvRequest<{
      minimumCharacters: number;
      debounceMs: number;
      pageSize: number;
    }>("/tvmaze/settings", controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setSettings(value);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setSettings({ minimumCharacters: 2, debounceMs: 300, pageSize: 8 });
      });
    return () => controller.abort();
  }, []);
  const [results, setResults] = useState<Search | null>(null),
    [state, setState] = useState(""),
    [active, setActive] = useState(-1),
    [visible, setVisible] = useState(8);
  useEffect(() => {
    setResults(null);
    setActive(-1);
    setVisible(settings?.pageSize ?? 8);
    if (
      !settings ||
      selected ||
      value.trim().length < settings.minimumCharacters
    ) {
      setState("");
      return;
    }
    const controller = new AbortController();
    setState("Searching…");
    const timer = setTimeout(() => {
      void tvRequest<Search>(
        "/tvmaze/shows?query=" + encodeURIComponent(value),
        controller.signal,
      )
        .then((result) => {
          if (controller.signal.aborted) return;
          setResults(result);
          setVisible(result.pageSize);
          setState(
            result.state === "PENDING"
              ? "Search is refreshing. Keep typing or try again shortly."
              : result.state.startsWith("STALE")
                ? "Showing saved matches; search is temporarily unavailable."
                : result.items.length
                  ? ""
                  : "No matches. You can keep this manual title.",
          );
        })
        .catch(() => {
          if (!controller.signal.aborted)
            setState("Search unavailable. You can keep this manual title.");
        });
    }, settings.debounceMs);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [value, selected, settings]);
  const items = results?.items.slice(0, visible) ?? [];
  function choose(candidate: Candidate) {
    onChange(candidate.name);
    onSelect(candidate);
    setResults(null);
    setState("");
  }
  return (
    <div className={styles.autocomplete}>
      <label htmlFor={id}>Title</label>
      <input
        id={id}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={items.length > 0}
        aria-controls={id + "-list"}
        aria-activedescendant={active >= 0 ? `${id}-${active}` : undefined}
        autoComplete="off"
        required
        value={value}
        disabled={disabled}
        onChange={(event) => {
          onChange(event.target.value);
          onSelect(null);
        }}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && items.length) {
            event.preventDefault();
            setActive((n) => Math.min(n + 1, items.length - 1));
          }
          if (event.key === "ArrowUp" && items.length) {
            event.preventDefault();
            setActive((n) => Math.max(n - 1, 0));
          }
          if (event.key === "Enter" && active >= 0 && items[active]) {
            event.preventDefault();
            choose(items[active]);
          }
          if (event.key === "Escape") {
            event.preventDefault();
            event.stopPropagation();
            setResults(null);
            setActive(-1);
          }
        }}
      />
      {state && <p role="status">{state}</p>}
      {items.length > 0 && (
        <ul id={id + "-list"} role="listbox" aria-label="Show matches">
          {items.map((candidate, index) => (
            <li
              id={`${id}-${index}`}
              key={candidate.tvmazeId}
              role="option"
              aria-selected={index === active}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(candidate)}
            >
              {candidate.posterUrl && (
                <img src={candidate.posterUrl} alt="" loading="lazy" />
              )}
              <span>
                {candidate.name}{" "}
                {candidate.premiereYear
                  ? `(${candidate.premiereYear})`
                  : "(Year unknown)"}
              </span>
            </li>
          ))}
        </ul>
      )}
      {results && visible < results.items.length && (
        <button
          type="button"
          className={styles.action}
          onClick={() => setVisible((n) => n + results.pageSize)}
        >
          More matches
        </button>
      )}
      {selected && (
        <p role="status">
          Selected: {selected.name}
          {selected.premiereYear ? ` (${selected.premiereYear})` : ""}{" "}
          <button
            type="button"
            className={styles.textAction}
            disabled={disabled}
            onClick={() => onSelect(null)}
          >
            Use manual title
          </button>
        </p>
      )}
    </div>
  );
}
