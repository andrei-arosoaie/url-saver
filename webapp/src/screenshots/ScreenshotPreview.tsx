import { useEffect, useRef, useState } from "react";
import { readScreenshot, type Screenshot, type ScreenshotReader } from "./api";
import styles from "./ScreenshotPreview.module.css";

export function ScreenshotPreview({
  articleId,
  read = readScreenshot,
}: {
  articleId: string;
  read?: ScreenshotReader;
}) {
  const [image, setImage] = useState<Screenshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [revision, setRevision] = useState(0);
  const refreshed = useRef(false);
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    refreshed.current = false;
    setImage(null);
    setFailed(false);
    dialog.current?.close();
  }, [articleId]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    void read(articleId, controller.signal, revision > 0)
      .then((value) => {
        if (!controller.signal.aborted) {
          setImage(value);
          setFailed(false);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [articleId, read, revision]);
  const refresh = () => {
    if (!refreshed.current) {
      refreshed.current = true;
      setRevision((v) => v + 1);
    } else {
      setFailed(true);
      dialog.current?.close();
    }
  };
  const available = !failed && image?.status === "AVAILABLE" && image.url;
  return (
    <section className={styles.root} aria-label="Saved screenshot">
      <h3>
        Screenshot <span>Visible viewport</span>
      </h3>
      {available ? (
        <>
          <button
            type="button"
            className={styles.preview}
            aria-label="Enlarge saved screenshot"
            onClick={() => {
              if (
                image.expiresAt &&
                Date.parse(image.expiresAt) <= Date.now() &&
                !refreshed.current
              )
                refresh();
              dialog.current?.showModal();
            }}
          >
            <img
              src={image.url!}
              alt="Saved page viewport"
              referrerPolicy="no-referrer"
              onError={refresh}
            />
            <span>Click to enlarge</span>
          </button>
          <dialog
            ref={dialog}
            className={styles.dialog}
            aria-label="Saved screenshot enlarged"
          >
            <button
              type="button"
              className={styles.close}
              onClick={() => dialog.current?.close()}
            >
              Close
            </button>
            <img
              src={image.url!}
              alt="Saved page viewport enlarged"
              referrerPolicy="no-referrer"
              onError={refresh}
            />
          </dialog>
        </>
      ) : (
        <div className={styles.placeholder} role="status">
          <svg role="img" aria-label="Missing screenshot" viewBox="0 0 64 48">
            <rect x="5" y="5" width="54" height="38" rx="4" />
            <path d="m8 36 14-14 10 10 9-8 15 14M5 3l54 42" />
            <circle cx="43" cy="16" r="4" />
          </svg>
          <strong>
            {loading ? "Loading screenshot..." : "Screenshot unavailable"}
          </strong>
          {!loading && <span>Article saved successfully.</span>}
        </div>
      )}
    </section>
  );
}
