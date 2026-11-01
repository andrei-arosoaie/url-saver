import { useEffect, useRef, useState } from "react";
import { profileApi, type Profile, type ProfileApi } from "./api";
import styles from "./ProfileMenu.module.css";

export function ProfileMenu({
  onSignOut,
  api = profileApi,
}: {
  onSignOut: () => Promise<void>;
  api?: ProfileApi;
}) {
  const [profile, setProfile] = useState<Profile>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [brokenPicture, setBrokenPicture] = useState(false);
  const menu = useRef<HTMLDetailsElement>(null);
  const request = useRef<AbortController | null>(null);
  const alive = useRef(true);
  async function load(signal: AbortSignal) {
    try {
      const value = await api.get(signal);
      if (!signal.aborted) {
        setProfile(value);
        setError("");
      }
    } catch (reason) {
      if (!signal.aborted)
        setError(
          reason instanceof Error
            ? reason.message
            : "Could not load your profile.",
        );
    }
  }
  useEffect(() => {
    alive.current = true;
    const controller = new AbortController();
    void load(controller.signal);
    const close = (event: PointerEvent) => {
      if (
        event.target instanceof Node &&
        !menu.current?.contains(event.target) &&
        menu.current
      )
        menu.current.open = false;
    };
    document.addEventListener("pointerdown", close);
    return () => {
      alive.current = false;
      controller.abort();
      request.current?.abort();
      document.removeEventListener("pointerdown", close);
    };
  }, [api]);
  async function act(action: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (reason) {
      if (alive.current)
        setError(
          reason instanceof Error ? reason.message : "Please try again.",
        );
    } finally {
      if (alive.current) setBusy(false);
    }
  }
  const name = profile?.displayName || "Your account";
  const initials = profile
    ? name
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => Array.from(part)[0])
        .join("")
        .toLocaleUpperCase()
    : "";
  return (
    <details
      className={styles.root}
      ref={menu}
      onKeyDown={(event) => {
        if (event.key === "Escape" && menu.current) {
          menu.current.open = false;
          menu.current.querySelector("summary")?.focus();
        }
      }}
    >
      <summary
        className={styles.avatar}
        aria-label={`Account menu for ${name}`}
        title={name}
      >
        {profile?.pictureUrl && !brokenPicture ? (
          <img
            src={profile.pictureUrl}
            alt=""
            referrerPolicy="no-referrer"
            onError={() => setBrokenPicture(true)}
          />
        ) : (
          initials || (
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              aria-hidden="true"
            >
              <circle cx="12" cy="8" r="4" />
              <path d="M4 22v-2a8 8 0 0 1 16 0v2" />
            </svg>
          )
        )}
      </summary>
      <div className={styles.popover} role="group" aria-label="Account options">
        <p className={styles.name}>{name}</p>
        {error && (
          <p className={styles.error} role="alert">
            {error}
          </p>
        )}
        {!profile && error && (
          <button
            disabled={busy}
            onClick={() =>
              void act(async () => {
                const controller = new AbortController();
                request.current = controller;
                await load(controller.signal);
              })
            }
          >
            Retry profile
          </button>
        )}
        {profile?.pictureUrl && !profile.pictureRemoved && (
          <button
            disabled={busy}
            onClick={() =>
              void act(async () => {
                const controller = new AbortController();
                request.current = controller;
                const updated = await api.removePicture(controller.signal);
                if (alive.current) {
                  setProfile(updated);
                  setBrokenPicture(false);
                }
              })
            }
          >
            Remove profile photo
          </button>
        )}
        {profile?.pictureRemoved && (
          <button
            disabled={busy}
            onClick={() =>
              void act(async () => {
                const controller = new AbortController();
                request.current = controller;
                const updated = await api.restorePicture(controller.signal);
                if (alive.current) {
                  setProfile(updated);
                  setBrokenPicture(false);
                }
              })
            }
          >
            Restore Google photo
          </button>
        )}
        <button disabled={busy} onClick={() => void act(onSignOut)}>
          Sign out
        </button>
      </div>
    </details>
  );
}
