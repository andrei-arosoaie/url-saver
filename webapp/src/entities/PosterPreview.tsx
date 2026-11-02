import { useEffect, useRef, useState } from "react";
import styles from "./Entities.module.css";
export function PosterPreview({
  src,
  originalSrc,
  title,
}: {
  src: string;
  originalSrc?: string | null;
  title: string;
}) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (!open) return;
    const node = dialog.current;
    node?.showModal();
    return () => {
      node?.close();
      trigger.current?.focus();
    };
  }, [open]);
  return (
    <>
      <button
        ref={trigger}
        type="button"
        className={styles.posterButton}
        aria-label={"Enlarge poster for " + title}
        onClick={() => setOpen(true)}
      >
        <img src={src} alt={title + " poster"} />
      </button>
      {open && (
        <dialog
          ref={dialog}
          className={styles.posterDialog}
          aria-label={"Poster for " + title}
          onCancel={() => setOpen(false)}
        >
          <button
            type="button"
            autoFocus
            className={styles.action}
            onClick={() => setOpen(false)}
          >
            Close poster
          </button>
          <img
            src={originalSrc || src}
            alt={title + " poster enlarged"}
            onError={({ currentTarget }) => {
              if (currentTarget.getAttribute("src") !== src)
                currentTarget.src = src;
            }}
          />
        </dialog>
      )}
    </>
  );
}
