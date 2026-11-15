import { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { Brand } from "../src/Brand";
import { ThemePicker } from "../src/theme/ThemePicker";
import { useTheme } from "../src/theme/useTheme";
import { TimelineList, type TimelineItem } from "../src/timeline/TimelineList";
import app from "../src/App.module.css";
import categories from "../src/categories/CategoriesPage.module.css";
import entities from "../src/entities/Entities.module.css";
import "../src/global.css";

// Proposed Phase 14 flow only. No Android intents, account access or network calls.
const catalog = [
  { key: "OTHER", displayName: "Other", color: "#66738d" },
  { key: "TECHNOLOGY", displayName: "Technology", color: "#2876dc" },
  { key: "BOOKS", displayName: "Books", color: "#9864bd" },
  { key: "MOVIES", displayName: "Movies", color: "#d08839" },
  { key: "TV_SHOWS", displayName: "TV shows", color: "#168e91" },
  { key: "GAMES", displayName: "Games", color: "#cf576b" },
  { key: "CUSTOM_RESEARCH", displayName: "My research", color: "#168e91" },
];

function SharePreview() {
  const scenario =
    new URLSearchParams(location.hash.slice(1)).get("scenario") ?? "new";
  const theme = useTheme();
  const [phase, setPhase] = useState(
    scenario === "signed-out" ? "signed-out" : "editing",
  );
  const [url, setUrl] = useState("https://example.com/postgres-indexes");
  const [title, setTitle] = useState("PostgreSQL indexes, explained");
  const [category, setCategory] = useState("OTHER");
  const [savedCategory, setSavedCategory] = useState("OTHER");
  const [message, setMessage] = useState("");
  const [online, setOnline] = useState(scenario !== "offline");
  const [confirmation, setConfirmation] = useState(false);
  const confirm = useRef<HTMLDialogElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  useEffect(() => {
    if (confirmation) confirm.current?.showModal();
  }, [confirmation]);
  const chosen = catalog.find((c) => c.key === category)!;
  const persisted = catalog.find((c) => c.key === savedCategory)!;
  const busy = phase === "saving";

  function save() {
    setMessage("");
    try {
      const parsed = new URL(url);
      if (!["http:", "https:"].includes(parsed.protocol) || url !== url.trim())
        throw Error();
    } catch {
      setMessage("Enter a valid HTTP or HTTPS link.");
      return;
    }
    if (!title.trim()) {
      setMessage("Enter a title before saving.");
      return;
    }
    setPhase("saving");
    timer.current = setTimeout(() => {
      if (!online) {
        setPhase("editing");
        setMessage(
          "You are offline. Your link, title and category are kept. Try again when connected.",
        );
        return;
      }
      if (scenario === "duplicate") {
        setPhase("duplicate");
        return;
      }
      setSavedCategory(category);
      setPhase("saved");
    }, 450);
  }

  const timeline: TimelineItem[] = [
    {
      id: "00000000-0000-4000-8000-000000000014",
      title,
      url,
      savedAt: "2026-11-08T09:12:00Z",
      version: 0,
      hasContent: false,
      entityName: null,
      category: savedCategory,
      categoryDisplay: persisted,
    },
  ];

  return (
    <div className={app.app}>
      <header className={app.header}>
        <div className={app.identity}>
          <div className={app.brand}>
            <Brand />
          </div>
          <small className={app.version}>Phase 14 · Interactive mock</small>
        </div>
        <div className={app.headerActions}>
          <ThemePicker
            preference={theme.preference}
            onChange={theme.setPreference}
          />
        </div>
      </header>
      <main className={`${categories.page} ${entities.hasSelection}`}>
        {phase === "signed-out" ? (
          <>
            <div className={categories.heading}>
              <div>
                <h1>Save to Know</h1>
                <p>
                  Your shared link is ready. Sign in to choose a category and
                  save it.
                </p>
              </div>
            </div>
            <article className={entities.detail}>
              <h3>Shared link</h3>
              <p>{title}</p>
              <p className={categories.note}>{url}</p>
              <p>Your draft stays here while you sign in.</p>
              <button
                className={`${categories.button} ${categories.primary}`}
                onClick={() => setPhase("editing")}
              >
                Sign in with Google
              </button>
            </article>
          </>
        ) : phase === "timeline" ? (
          <>
            <div className={categories.heading}>
              <div>
                <h1>Saved entries</h1>
                <p>Timeline · {persisted.displayName} · Link only</p>
              </div>
            </div>
            <TimelineList
              items={timeline}
              selected={null}
              onSelect={() => setPhase("reader")}
              now={new Date("2026-11-08T10:00:00Z")}
            />
            <button
              className={categories.button}
              onClick={() => {
                setPhase("editing");
                setMessage("");
              }}
            >
              Share another link
            </button>
          </>
        ) : phase === "reader" ? (
          <>
            <button
              className={categories.button}
              onClick={() => setPhase("timeline")}
            >
              ← Timeline
            </button>
            <div className={categories.heading}>
              <div>
                <h1>{title}</h1>
                <p>{persisted.displayName} · Link only</p>
              </div>
            </div>
            <p>{url}</p>
            <p className={categories.note}>
              The link and title were saved. Article text and a screenshot were
              not shared.
            </p>
          </>
        ) : phase === "saved" || phase === "updated" ? (
          <>
            <div className={categories.heading}>
              <div>
                <h1>
                  {phase === "updated"
                    ? "Category updated"
                    : "Saved to Timeline"}
                </h1>
                <p>Saved to Alex Morgan's Know library.</p>
              </div>
            </div>
            <article className={entities.detail}>
              <h3>{title}</h3>
              <p>{url}</p>
              <p className={categories.name}>
                <span
                  className={categories.dot}
                  style={{ background: persisted.color }}
                />
                {persisted.displayName}
              </p>
              <p className={categories.note}>
                Link only · No article text or screenshot
              </p>
            </article>
            <p className={categories.actions}>
              <button
                className={`${categories.button} ${categories.primary}`}
                onClick={() => setPhase("timeline")}
              >
                View in Timeline
              </button>
              <button
                className={categories.button}
                onClick={() => setPhase("closed")}
              >
                Done
              </button>
            </p>
          </>
        ) : phase === "duplicate" ? (
          <>
            <div className={categories.heading}>
              <div>
                <h1>Already in your timeline</h1>
                <p>The existing link has been kept. Nothing was overwritten.</p>
              </div>
            </div>
            <article className={entities.detail}>
              <h3>{title}</h3>
              <p>Current category: Other</p>
              <p>Selected category: {chosen.displayName}</p>
              <div className={categories.actions}>
                <button
                  className={`${categories.button} ${categories.primary}`}
                  onClick={() => {
                    setSavedCategory("OTHER");
                    setPhase("timeline");
                  }}
                >
                  View existing
                </button>
                {category !== "OTHER" && (
                  <button
                    className={categories.button}
                    onClick={() => setConfirmation(true)}
                  >
                    Change category
                  </button>
                )}
                <button
                  className={categories.button}
                  onClick={() => setPhase("editing")}
                >
                  Back
                </button>
              </div>
            </article>
          </>
        ) : phase === "closed" ? (
          <>
            <div className={categories.heading}>
              <div>
                <h1>Returned to the source app</h1>
                <p>
                  In the Android application, Back or Done returns to the sender
                  when available.
                </p>
              </div>
            </div>
            <button
              className={categories.button}
              onClick={() => setPhase("editing")}
            >
              Open share mock again
            </button>
          </>
        ) : (
          <>
            <div className={categories.heading}>
              <div>
                <h1>Save to Know</h1>
                <p>Choose a category for this shared link.</p>
              </div>
            </div>
            <div className={entities.workspace}>
              <section
                className={entities.detail}
                aria-label="Shared link form"
              >
                <form
                  className={`${categories.form} ${entities.form}`}
                  onSubmit={(event) => {
                    event.preventDefault();
                    save();
                  }}
                >
                  <label>
                    Link
                    <input
                      type="url"
                      value={url}
                      onChange={(event) => setUrl(event.target.value)}
                      disabled={busy}
                      required
                    />
                  </label>
                  <label>
                    Title
                    <input
                      value={title}
                      onChange={(event) => setTitle(event.target.value)}
                      disabled={busy}
                      required
                    />
                  </label>
                  <label>
                    Category
                    <select
                      value={category}
                      onChange={(event) => setCategory(event.target.value)}
                      disabled={busy}
                      required
                    >
                      {catalog.map((c) => (
                        <option key={c.key} value={c.key}>
                          {c.displayName}
                        </option>
                      ))}
                    </select>
                  </label>
                  <p className={categories.note}>
                    Saves a link and title. Article text and screenshots are not
                    included.
                  </p>
                  {message && (
                    <p className={categories.error} role="alert">
                      {message}
                      {!online && (
                        <>
                          <br />
                          <button
                            type="button"
                            className={categories.button}
                            onClick={() => {
                              setOnline(true);
                              setMessage("");
                            }}
                          >
                            Try again when connected
                          </button>
                        </>
                      )}
                    </p>
                  )}
                  <div className={categories.actions}>
                    <button
                      className={`${categories.button} ${categories.primary}`}
                      disabled={busy}
                      type="submit"
                    >
                      {busy ? "Saving…" : "Save to Timeline"}
                    </button>
                    <button
                      type="button"
                      className={categories.button}
                      disabled={busy}
                      onClick={() => setPhase("closed")}
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              </section>
              <aside className={entities.detail}>
                <h3>Your library</h3>
                <p>Alex Morgan</p>
                <p className={categories.note}>Category</p>
                <p className={categories.name}>
                  <span
                    className={categories.dot}
                    style={{ background: chosen.color }}
                  />
                  {chosen.displayName}
                </p>
                <p className={categories.note}>
                  The chosen category will appear on the timeline entry.
                </p>
              </aside>
            </div>
          </>
        )}
        {confirmation && (
          <dialog
            ref={confirm}
            className={categories.modal}
            onCancel={() => setConfirmation(false)}
          >
            <h2>Change the existing category?</h2>
            <p>
              Move this link from Other to {chosen.displayName}? Its saved
              content and original save date stay unchanged.
            </p>
            <div className={categories.actions}>
              <button
                className={`${categories.button} ${categories.primary}`}
                onClick={() => {
                  setConfirmation(false);
                  setSavedCategory(category);
                  setPhase("updated");
                }}
              >
                Confirm category change
              </button>
              <button
                className={categories.button}
                onClick={() => setConfirmation(false)}
              >
                Cancel
              </button>
            </div>
          </dialog>
        )}
      </main>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(<SharePreview />);
window.addEventListener("hashchange", () => location.reload());
let pending = false;
new MutationObserver(() => {
  if (pending) return;
  pending = true;
  setTimeout(() => {
    pending = false;
    if (parent !== window)
      parent.postMessage(
        {
          type: "know-share-mock-ready",
          width: innerWidth,
          text: document.body.innerText,
        },
        "*",
      );
  }, 0);
}).observe(document.getElementById("root")!, {
  childList: true,
  subtree: true,
  characterData: true,
});
