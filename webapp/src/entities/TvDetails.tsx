import { useEffect, useRef, useState } from "react";
import { requiresReload, EntityApiError } from "./api";
import type { Entity } from "./model";
import { TvAutocomplete } from "./TvAutocomplete";
import {
  tvRequest,
  tvPath,
  type Candidate,
  type TvDetails as Details,
  type Season,
  type Episode,
} from "./tvApi";
import styles from "./Entities.module.css";

export function TvDetails({
  entity,
  revision,
  onChanged,
}: {
  entity: Entity;
  revision: number;
  onChanged: () => void;
}) {
  const [data, setData] = useState<Details | null>(null),
    [unmatched, setUnmatched] = useState(false),
    [error, setError] = useState<unknown>(null),
    [busy, setBusy] = useState(false),
    [reload, setReload] = useState(0);
  const [seasons, setSeasons] = useState<Season[]>([]),
    [episodes, setEpisodes] = useState<Episode[]>([]),
    [season, setSeason] = useState(""),
    [positionSeason, setPositionSeason] = useState(""),
    [positionEpisodes, setPositionEpisodes] = useState<Episode[]>([]),
    [episode, setEpisode] = useState("");
  const [alias, setAlias] = useState(""),
    [query, setQuery] = useState(""),
    [candidate, setCandidate] = useState<Candidate | null>(null),
    [changing, setChanging] = useState(false),
    [confirmation, setConfirmation] = useState<"change" | "bulk" | null>(null);
  const write = useRef<AbortController | null>(null);
  const previousAlias = useRef<string | undefined>(undefined);
  const confirmDialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (!confirmation) return;
    const dialog = confirmDialog.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, [confirmation]);
  const path = tvPath(entity.id);
  useEffect(() => () => write.current?.abort(), []);
  useEffect(() => {
    const controller = new AbortController();
    setError(null);
    void tvRequest<Details>(path, controller.signal)
      .then((fresh) => {
        if (!controller.signal.aborted) {
          setData(fresh);
          const oldAlias = previousAlias.current;
          setAlias((value) =>
            oldAlias !== undefined && value !== oldAlias
              ? value
              : (fresh.alias ?? ""),
          );
          previousAlias.current = fresh.alias ?? "";
          setUnmatched(false);
        }
      })
      .catch((cause) => {
        if (!controller.signal.aborted) {
          if (cause instanceof EntityApiError && cause.status === 404)
            setUnmatched(true);
          else setError(cause);
        }
      });
    return () => controller.abort();
  }, [path, revision, reload]);
  useEffect(() => {
    if (!data) return;
    const controller = new AbortController();
    void tvRequest<{ items: Season[] }>(path + "/seasons", controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setSeasons(result.items);
          setSeason((value) =>
            result.items.some((s) => String(s.id) === value)
              ? value
              : String(result.items[0]?.id ?? ""),
          );
        }
      })
      .catch((cause) => {
        if (!controller.signal.aborted) setError(cause);
      });
    return () => controller.abort();
  }, [path, data]);
  useEffect(() => {
    setEpisodes([]);
    if (!season) return;
    const controller = new AbortController();
    void tvRequest<{ items: Episode[] }>(
      `${path}/seasons/${season}/episodes`,
      controller.signal,
    )
      .then((result) => {
        if (!controller.signal.aborted) setEpisodes(result.items);
      })
      .catch((cause) => {
        if (!controller.signal.aborted) setError(cause);
      });
    return () => controller.abort();
  }, [path, season, data]);
  useEffect(() => {
    if (!positionSeason) {
      setPositionEpisodes([]);
      setEpisode("");
      return;
    }
    const controller = new AbortController();
    void tvRequest<{ items: Episode[] }>(
      `${path}/seasons/${positionSeason}/episodes`,
      controller.signal,
    )
      .then((result) => {
        if (!controller.signal.aborted) {
          const available = result.items.filter(
            (e) => e.airAt && e.canMarkWatched,
          );
          setPositionEpisodes(available);
          setEpisode((value) =>
            available.some((e) => String(e.id) === value) ? value : "",
          );
        }
      })
      .catch((cause) => {
        if (!controller.signal.aborted) setError(cause);
      });
    return () => controller.abort();
  }, [path, positionSeason, data]);
  // Keep waiting through transient refresh failures so the catalog can recover without a reload.
  useEffect(() => {
    if (!data || data.metadata.state === "READY") return;
    const timer = setTimeout(() => setReload((n) => n + 1), 5000);
    return () => clearTimeout(timer);
  }, [data, reload]);
  async function mutate(suffix: string, method: string, body: object) {
    const controller = new AbortController();
    write.current = controller;
    setBusy(true);
    setError(null);
    try {
      const fresh = await tvRequest<Details>(
        path + suffix,
        controller.signal,
        method,
        body,
      );
      if (!controller.signal.aborted) {
        setData(fresh);
        setUnmatched(false);
        setChanging(false);
        setConfirmation(null);
        onChanged();
      }
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  const blocked = busy || requiresReload(error);
  const selectedSeason = seasons.find((s) => String(s.id) === season);
  return (
    <section className={styles.tvDetails} aria-label="Viewing progress">
      {error instanceof Error && (
        <p role="alert">
          {error.message}{" "}
          <button type="button" onClick={() => setReload((n) => n + 1)}>
            Reload show
          </button>
        </p>
      )}
      {!data && !unmatched && !error && <p role="status">Loading show…</p>}
      {data && (
        <>
          <header className={styles.tvHeader}>
            {data.show?.posterUrl && <img src={data.show.posterUrl} alt="" />}
            <div>
              <h4>
                {data.canonicalTitle}
                {data.alias ? ` · ${data.alias}` : ""}
              </h4>
              <p>{data.show?.genres.join(" · ")}</p>
              <p>
                {data.show?.providerStatus}
                {data.show?.averageRuntime
                  ? ` · ${data.show.averageRuntime} min`
                  : ""}
              </p>
              {data.show?.tvmazeRating != null && (
                <p>Rating: {data.show.tvmazeRating}/10</p>
              )}
              {data.show?.imdbUrl && (
                <a href={data.show.imdbUrl} target="_blank" rel="noreferrer">
                  IMDb ↗
                </a>
              )}
            </div>
          </header>
          {data.metadata.state !== "READY" && (
            <p role="status">
              {data.metadata.fetchedAt
                ? "Showing saved information while seasons and episodes refresh."
                : data.metadata.state === "PENDING"
                  ? "Loading seasons and episodes…"
                  : "Seasons and episodes are temporarily unavailable. Retrying…"}
            </p>
          )}
          {data.newEpisodesAvailable && (
            <p className={styles.newEpisodes}>New episodes available</p>
          )}
          {data.show?.nextEpisode && (
            <p>
              Next episode: S{data.show.nextEpisode.season}E
              {data.show.nextEpisode.number ?? "?"} ·{" "}
              {data.show.nextEpisode.title}
              {data.show.nextEpisode.airAt
                ? ` · ${new Date(data.show.nextEpisode.airAt).toLocaleString()}`
                : ""}
            </p>
          )}
          {data.show?.summary && (
            <details>
              <summary>Synopsis</summary>
              <p>{data.show.summary}</p>
            </details>
          )}
          <p>
            Saved position:{" "}
            {entity.tvShow?.position ??
              (data.lastWatchedEpisodeId
                ? `Episode ${data.lastWatchedEpisodeId}`
                : "Not set")}{" "}
            · {data.watchedCount} watched / {data.airedCount} aired
          </p>
          <fieldset disabled={blocked || !seasons.length}>
            <legend>Last watched</legend>
            <label>
              Season
              <select
                value={positionSeason}
                onChange={(event) => {
                  setPositionSeason(event.target.value);
                  setPositionEpisodes([]);
                  setEpisode("");
                }}
              >
                <option value="">
                  {!seasons.length && !data.metadata.fetchedAt
                    ? "Loading seasons…"
                    : "Choose season"}
                </option>
                {seasons.map((s) => (
                  <option key={s.id} value={s.id}>
                    Season {s.number}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Episode
              <select
                disabled={!positionEpisodes.length}
                value={episode}
                onChange={(event) => setEpisode(event.target.value)}
              >
                <option value="">
                  {positionSeason && !positionEpisodes.length
                    ? "No aired episodes"
                    : "Choose episode"}
                </option>
                {positionEpisodes.map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.number ?? "?"} · {e.title}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className={styles.action}
              disabled={!episode}
              onClick={() =>
                void mutate("/position", "PATCH", {
                  version: data.version,
                  episodeId: Number(episode),
                  markPreviousWatched: false,
                })
              }
            >
              Save position only
            </button>
            <button
              type="button"
              className={styles.action}
              disabled={!episode}
              onClick={() => setConfirmation("bulk")}
            >
              Save and mark previous watched…
            </button>
          </fieldset>
          <button
            type="button"
            className={styles.action}
            disabled={blocked || !data.canMarkSeen || data.status === "SEEN"}
            onClick={() =>
              void mutate("/mark-seen", "POST", {
                version: data.version,
                entityVersion: data.entityVersion,
              })
            }
          >
            Mark show seen
          </button>
          <details>
            <summary>
              Episodes · Season {selectedSeason?.number ?? "—"} ·{" "}
              {selectedSeason?.airedCount ?? 0} aired
            </summary>
            <label>
              Browse season
              <select
                value={season}
                disabled={blocked}
                onChange={(event) => setSeason(event.target.value)}
              >
                {seasons.map((s) => (
                  <option key={s.id} value={s.id}>
                    Season {s.number}
                  </option>
                ))}
              </select>
            </label>
            {episodes.map((e) => (
              <article key={e.id} className={styles.tvEpisode}>
                {e.imageUrl && <img src={e.imageUrl} alt="" loading="lazy" />}
                <div>
                  <label>
                    <input
                      type="checkbox"
                      checked={e.watched}
                      disabled={blocked || (!e.watched && !e.canMarkWatched)}
                      onChange={(event) =>
                        void mutate(`/episodes/${e.id}/watched`, "PUT", {
                          version: data.version,
                          watched: event.target.checked,
                        })
                      }
                    />{" "}
                    E{e.number ?? "?"} · {e.title}
                  </label>
                  <small>
                    {e.airAt
                      ? new Date(e.airAt).toLocaleString()
                      : e.airDate
                        ? `${e.airDate} · Air time unknown`
                        : "Air date unknown"}
                    {!e.canMarkWatched ? " · Not yet aired" : ""}
                  </small>
                  {e.summary && (
                    <details>
                      <summary>Episode synopsis</summary>
                      <p>{e.summary}</p>
                    </details>
                  )}
                </div>
              </article>
            ))}
          </details>
          <details>
            <summary>Edit show</summary>
            <form
              className={styles.form}
              onSubmit={(event) => {
                event.preventDefault();
                void mutate("", "PATCH", {
                  version: data.version,
                  alias: alias || null,
                });
              }}
            >
              <label>
                Alias
                <input
                  value={alias}
                  disabled={blocked}
                  onChange={(event) => setAlias(event.target.value)}
                />
              </label>
              <button className={styles.action} disabled={blocked}>
                Save alias
              </button>
            </form>
            <button
              type="button"
              className={styles.action}
              disabled={blocked}
              onClick={() => {
                setChanging((value) => !value);
                setCandidate(null);
                setQuery("");
              }}
            >
              Change show
            </button>
          </details>
        </>
      )}
      {(unmatched || changing) && (
        <div>
          <p>
            {unmatched
              ? "This manual title is not linked to a show. Select a match to load episodes."
              : "Select the correct show."}
          </p>
          <TvAutocomplete
            value={query}
            onChange={setQuery}
            selected={candidate}
            onSelect={setCandidate}
            disabled={blocked}
          />
          <button
            type="button"
            className={styles.action}
            disabled={blocked || !candidate}
            onClick={() =>
              data
                ? setConfirmation("change")
                : void mutate("", "PUT", {
                    entityVersion: entity.version,
                    tvmazeId: candidate!.tvmazeId,
                  })
            }
          >
            {data ? "Review replacement…" : "Link selected show"}
          </button>
          {changing && (
            <button type="button" onClick={() => setChanging(false)}>
              Cancel change
            </button>
          )}
        </div>
      )}
      {confirmation && (
        <dialog
          ref={confirmDialog}
          onCancel={(event) => {
            if (busy) event.preventDefault();
            else setConfirmation(null);
          }}
          aria-modal="true"
          aria-labelledby="tv-confirm-heading"
          className={styles.confirmation}
        >
          <h4 id="tv-confirm-heading">
            {confirmation === "change"
              ? "Replace this show?"
              : "Mark previous episodes watched?"}
          </h4>
          <p>
            {confirmation === "change"
              ? "This resets the saved position and episode checkmarks and sets the show to To be seen. Your alias, linked pages and completion history are preserved."
              : "All aired regular episodes through this position will be marked watched. Existing checkmarks and completion history are preserved."}
          </p>
          <button
            type="button"
            autoFocus
            disabled={busy}
            onClick={() => setConfirmation(null)}
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={blocked}
            onClick={() => {
              if (!data) return;
              void (confirmation === "change"
                ? mutate("/change-show", "POST", {
                    version: data.version,
                    entityVersion: data.entityVersion,
                    tvmazeId: candidate!.tvmazeId,
                    confirmResetProgress: true,
                  })
                : mutate("/position", "PATCH", {
                    version: data.version,
                    episodeId: Number(episode),
                    markPreviousWatched: true,
                  }));
            }}
          >
            Confirm
          </button>
        </dialog>
      )}
    </section>
  );
}
