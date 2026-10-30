# Know webapp

Responsive Timeline, article reader, Entities and Categories. React + TypeScript, Vite, CSS Modules, native fetch, Vitest and React Testing Library.

## Build and verify

Use Node.js LTS and npm from this folder:

```sh
npm ci
npm run check
```

Types are generated during builds from ../server/openapi/openapi.yaml. Generated types and dist are not committed. Build the webapp before the server: Gradle packages dist into the Spring Boot JAR, serving the page and API from the same origin.

For development, npm run dev proxies /api to localhost:8080. Set KNOW_DEV_API_ORIGIN before starting Vite to use another local backend.

## Behavior

- Timeline: newest-first cursor pagination, category/search filters, explicit versioned metadata edits, reader and manual retries.
- Entities: exactly one type, Table/Board/By year, manual creation, editable details, type-specific statuses, completion history and linked source pages.
- Completing a status records server time automatically. Past completions can be recorded, corrected with microsecond UTC precision or removed without rewriting current status.
- Timeline Add/Remove changes only the association. Existing entities and history survive unlinking.
- Categories: dedicated page with article counts, custom creation, name/color editing, disable/re-enable and confirmed removal. Categories containing articles cannot be disabled or removed; Other stays enabled for new saves.
- Delete article requires confirmation and permanently removes its saved data, screenshot and associations. Entities and completion history remain. Stale or unconfirmed deletions require reloading before another confirmation.
- Bookmarkable article/entity routes and browser Back/Forward preserve navigation state. Mobile views adapt to the available width.
- Server-sanitized article HTML uses a script-disabled sandboxed frame and restrictive content security policy. No automatic remote resources.
- Version conflicts require explicit reload; writes never automatically retry. Failed or stale requests do not display another record as the requested one.
- System/light/dark themes; only the theme preference is stored locally.

The approved temporary account is shared with the extension. Authentication and entity deletion remain deferred; permanent article deletion and individual completion correction/removal work.

## Firefox end-to-end verification

`npm run test:browser` exercises the real Categories page and article-deletion confirmation against an isolated running backend and PostgreSQL. It creates fixtures and refuses non-loopback origins. Supply `KNOW_IT_API_ORIGIN`, `KNOW_BROWSER_PROOF_DIR` and optionally `KNOW_FIREFOX_BIDI_URL` (default `ws://127.0.0.1:9224/session`). Launch a separate headless Firefox profile with its remote debugging port first. The script verifies the complete category lifecycle, protected in-use categories, article deletion, entity preservation and desktop/mobile overflow, and writes screenshots for visual review. It never uses Supabase or production ImageKit.

The visual references are ../web-mockups.html and ../entities-mockups.html. Timeline excerpts await a decision. Renaming an article or entity never changes existing associations. Links use stable IDs and change only through explicit link/unlink actions.

The Supabase-backed local preview is http://127.0.0.1:18084. Automated checks include 61 webapp tests; backend integration tests cover complete HTTP/database flows. Desktop/mobile browser flows were verified against disposable PostgreSQL without inserting test entities into Supabase. See ../server/IMPLEMENTATION-STATUS.md.
