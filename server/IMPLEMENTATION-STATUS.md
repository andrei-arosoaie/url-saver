# Implementation status

Phases 1–4, including screenshot URL caching, are deployed to Render (image 1adc8ebe88248736ba715924f51febdb069b97c6). Phase 5 backend and webapp are implemented locally on feature/phase-5-categories-and-deletion; it is not deployed.

## Phase 5 final review checkpoint

- Approved category contracts implemented: counts, custom creation, versioned editing for all categories, disable/re-enable, and empty-custom-category removal. Account-scoped names are unique ignoring case and surrounding whitespace; the configurable name limit defaults to 100 Unicode code points.
- Referenced categories cannot be disabled or removed. Other remains enabled for uncategorized saves; predefined categories cannot be deleted. Renaming preserves stable keys and all associations.
- Versioned article DELETE removes article data and associations, preserves entities/completion history, and queues provider-independent screenshot cleanup in the same transaction. Failed cleanup retries durably; late uploads remain tracked and cannot recreate deleted articles.
- Migration V5 adds category-name uniqueness. All verification uses disposable databases; production remains at V4.
- All 34 unit/architecture tests and 45 complete integration tests pass with the shared Spring/PostgreSQL context. OpenAPI exported, JAR packaged, and Docker image smoke verification passed.
- Dedicated responsive Categories page and confirmed permanent article deletion are implemented. Counts link to the filtered timeline; category edits update labels/colors without changing associations. Stale or unconfirmed writes require reload.
- All 82 webapp tests, generated types, TypeScript, ESLint, Prettier and production build pass. Real Firefox 158.0.1 verified creation, assignment, rename/recolor, in-use protection, cancellation, permanent deletion, entity preservation, disable/re-enable and category removal. Desktop/mobile screenshots and dialogs were reviewed with no horizontal overflow.
- Backend review is approved. Final user review remains before committing and deploying Phase 5.

## Working functionality

- Firefox saves URL, title and Readability HTML through the approved temporary account. Duplicate confirmation and explicit versioned updates preserve existing content.
- The responsive React webapp is served by Spring Boot. Timeline supports category/search filters, reading, versioned metadata edits, browser navigation and system/light/dark themes.
- Entities supports Book/Movie/TV show/Game, Table/Board/By year, manual creation, versioned title/status edits, repeated completions, precise UTC history correction/removal and linked source pages.
- Timeline Add/Remove links or unlinks an entity without deleting either record or completion history.
- AI is included in the eight-category catalog. Supabase public has migrations through V4 applied; existing article content is preserved.

- Renaming an article or entity preserves every existing association. Links use stable IDs and change only through explicit link/unlink actions.

## Verification

- Backend: 79 passing tests (34 unit/architecture, 45 integration), one shared Spring context and disposable PostgreSQL through Debian WSL Docker. Integration tests exercise complete HTTP/application/database flows, concurrency, ownership, versions, history, links, filtering, categories, permanent deletion and screenshot cleanup.
- Webapp: 82 passing tests; generated types, TypeScript, ESLint, Prettier and production build pass.
- Firefox: 64 passing tests; generated types, TypeScript, ESLint, Prettier and production build pass.
- Browser verification against disposable PostgreSQL covers desktop/mobile views, manual creation, status transitions, repeated and historical completions, microsecond timestamp correction, version conflicts, source navigation and Add/Remove preservation. No test entities, history or links were written to Supabase.
- Live Supabase verification: eight real saved articles and all eight categories loaded; Entities returned its genuine empty state. No sample records were inserted.
- The local Supabase preview is stopped to avoid exhausting session-pool connections. Production webapp: https://url-saver-server.onrender.com/.

## Phase 3 verification

- Pending URL/title/content, attempts and deadlines survive browser/extension restarts through Firefox local storage and background alarms. One initial attempt plus ten retries; each retry waits 60 seconds after non-response. HTTP errors stop retries. Explicit updates are never replayed.
- Exact-URL pending jobs are shared across tabs/clicks; original captures and retry budgets are retained. Confirmed success/final failure clear captured text; duplicates require explicit review.
- All 64 extension unit/UI, contract, persistent-queue and viewport-guard tests pass. Five complete React/background/HTTP/PostgreSQL flows pass in Debian WSL Docker, including restart recovery, lost post-commit responses, exhaustion and validation. The CI-equivalent image harness and direct SQL assertions pass.
- Development XPI: extension/firefox/.output/know-development.xpi. Actual Firefox desktop/Android restart and installation verification remains a user review checkpoint.

## Remaining

- Renaming an article or entity preserves every existing association. Links use stable IDs and change only through explicit link/unlink actions.
- Timeline excerpts await a product decision; the current list API supplies no excerpt.
- Real authentication and Firefox desktop/Android device verification remain outstanding.
- The under-five-second save-ready startup target remains unmet.

## Phase 4 decisions

ImageKit is selected for screenshot storage behind a provider-independent application interface. ImageKit SDK/API types and credentials stay in its infrastructure adapter. Upload, deletion and temporary URL delivery use the interface; clients and article use cases remain provider-independent. Persist provider ID and opaque file reference; select implementations through mounted YAML. Switching providers does not migrate existing files automatically.

The extension sends losslessly compressed native-resolution PNG. All final image transformations are backend-only YAML configuration (initially JPEG quality 90; PNG selectable later). No screenshot-settings endpoint. Incoming upload limit is 20 MiB, configurable independently from the 5 MiB stored-image limit.

Phase 4 uses private ImageKit storage through the official SDK and a provider-independent application interface. Backend transformation preserves native dimensions, enforces input/output/decoded-pixel limits, and bounds processing concurrency. Independent screenshot versions and a durable cleanup journal support retries and replacement. Live private upload, signed delivery, unsigned denial and cleanup were verified. Actual desktop Firefox 158.0.1 passed native 2560 x 1440 capture at display density 2, with pixel checks confirming Know controls were excluded and restored. Android capture and installed-extension restart verification remain manual checks.

## Screenshot delivery caching

Deployed: signed URLs are reused by account, screenshot version and immutable file reference until five seconds before their configured expiry. Backend capacity defaults to 1000 through know.screenshots.signed-url-cache-entries. Every request still checks current ownership/state; metadata stays no-store. The webapp keeps up to 100 URL records and uses normal browser HTTP image caching. Replacements, missing images and denied access invalidate reuse. Image errors explicitly request refresh=true to renew the backend URL. Provider SDK details remain outside clients. Existing ImageKit CDN caching benefits from stable URLs; CDN delivery still consumes bandwidth.

Verification: 29 backend unit/architecture tests, 38 full integration tests and 68 webapp tests pass. OpenAPI export, types, lint, formatting and builds pass. No schema migration is needed. These caching changes are deployed in image 1adc8ebe88248736ba715924f51febdb069b97c6.
