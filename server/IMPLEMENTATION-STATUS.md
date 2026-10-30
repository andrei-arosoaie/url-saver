# Implementation status

Phase 2 Timeline and Entities are deployed to Render (image b525dabc938a4477559ba36b01fa1da96ac705d7). Phase 3 persistent extension retries are implemented locally on feature/extension-retries; the updated extension must be installed separately.

## Working functionality

- Firefox saves URL, title and Readability HTML through the approved temporary account. Duplicate confirmation and explicit versioned updates preserve existing content.
- The responsive React webapp is served by Spring Boot. Timeline supports category/search filters, reading, versioned metadata edits, browser navigation and system/light/dark themes.
- Entities supports Book/Movie/TV show/Game, Table/Board/By year, manual creation, versioned title/status edits, repeated completions, precise UTC history correction/removal and linked source pages.
- Timeline Add/Remove links or unlinks an entity without deleting either record or completion history.
- AI is included in the eight-category catalog. Supabase public has migrations V2 and V3 applied; existing article content is preserved.

- Renaming an article or entity preserves every existing association. Links use stable IDs and change only through explicit link/unlink actions.

## Verification

- Backend: 67 passing tests (29 unit/architecture, 38 integration), one shared Spring context and disposable PostgreSQL through Debian WSL Docker. Integration tests exercise complete HTTP/application/database flows, concurrency, ownership, versions, history, links, filtering and constraints. OpenAPI drift and JAR packaging pass.
- Webapp: 68 passing tests; generated types, TypeScript, ESLint, Prettier and production build pass.
- Firefox: 64 passing tests; generated types, TypeScript, ESLint, Prettier and production build pass.
- Browser verification against disposable PostgreSQL covers desktop/mobile views, manual creation, status transitions, repeated and historical completions, microsecond timestamp correction, version conflicts, source navigation and Add/Remove preservation. No test entities, history or links were written to Supabase.
- Live Supabase verification: eight real saved articles and all eight categories loaded; Entities returned its genuine empty state. No sample records were inserted.
- The local Supabase preview is stopped to avoid exhausting session-pool connections. Production webapp: https://url-saver-server.onrender.com/.

## Phase 3 verification

- Pending URL/title/content, attempts and deadlines survive browser/extension restarts through Firefox local storage and background alarms. One initial attempt plus ten retries; each retry waits 60 seconds after non-response. HTTP errors stop retries. Explicit updates are never replayed.
- Exact-URL pending jobs are shared across tabs/clicks; original captures and retry budgets are retained. Confirmed success/final failure clear captured text; duplicates require explicit review.
- All 51 extension unit/UI, contract, persistent-queue and viewport-guard tests pass. Four complete React/background/HTTP/PostgreSQL flows pass in Debian WSL Docker, including restart recovery, lost post-commit responses, exhaustion and validation. The CI-equivalent image harness and direct SQL assertions pass.
- Development XPI: extension/firefox/.output/know-development.xpi. Actual Firefox desktop/Android restart and installation verification remains a user review checkpoint.

## Remaining

- Renaming an article or entity preserves every existing association. Links use stable IDs and change only through explicit link/unlink actions.
- Timeline excerpts await a product decision; the current list API supplies no excerpt.
- Real authentication and Firefox desktop/Android device verification remain outstanding.
- Startup remains above the under-five-second target; the prior Supabase startup reported 10.41 seconds. This is not a save-ready benchmark.

## Phase 4 decisions

ImageKit is selected for screenshot storage behind a provider-independent application interface. ImageKit SDK/API types and credentials stay in its infrastructure adapter. Upload, deletion and temporary URL delivery use the interface; clients and article use cases remain provider-independent. Persist provider ID and opaque file reference; select implementations through mounted YAML. Switching providers does not migrate existing files automatically.

The extension sends losslessly compressed native-resolution PNG. All final image transformations are backend-only YAML configuration (initially JPEG quality 90; PNG selectable later). No screenshot-settings endpoint. Incoming upload limit is 20 MiB, configurable independently from the 5 MiB stored-image limit.

Phase 4 backend foundation now includes the provider-independent ScreenshotStorage interface, an official ImageKit Java SDK 3.0.0 adapter (private uploads, signing and deletion), and a backend-native PNG transformer. The transformer preserves dimensions, selects JPEG/PNG and quality on the server, and enforces independent input, output and decoded-pixel limits with bounded processing concurrency. Screenshot GET/PUT endpoints, owned database associations, independent versions and a persisted cleanup journal are implemented. Four complete screenshot flows pass against PostgreSQL and a local provider fixture using the actual official SDK. All 26 unit/architecture tests and 38 integration tests pass; OpenAPI export and JAR packaging pass. Live ImageKit verification passed: private SDK upload, backend JPEG transformation, 30-minute signed download, unsigned-access rejection and SDK deletion of the test file. Firefox Save now captures native PNG, uploads multipart after article success, journals independent image retries across restarts, and replaces screenshots only after explicit update. All five client-to-HTTP/PostgreSQL integration flows pass, including private JPEG delivery through the official SDK fixture. Webapp verification passes (64 tests, lint, formatting, types and build). Actual desktop Firefox 158.0.1 passed native 2560 x 1440 capture at display density 2, article persistence, storage delivery and pixel-level verification that Know controls were excluded and restored. A real-browser check found and fixed the host-visibility issue: capture now hides the whole host with display:none. Android capture, installed-extension restart verification and deployment remain the review checkpoint.

## Screenshot delivery caching

Implemented locally: signed URLs are reused by account, screenshot version and immutable file reference until five seconds before their configured expiry. Backend capacity defaults to 1000 through know.screenshots.signed-url-cache-entries. Every request still checks current ownership/state; metadata stays no-store. The webapp keeps up to 100 URL records and uses normal browser HTTP image caching. Replacements, missing images and denied access invalidate reuse. Image errors explicitly request refresh=true to renew the backend URL. Provider SDK details remain outside clients. Existing ImageKit CDN caching benefits from stable URLs; CDN delivery still consumes bandwidth.

Verification: 29 backend unit/architecture tests, 38 full integration tests and 68 webapp tests pass. OpenAPI export, types, lint, formatting and builds pass. No schema migration is needed. These caching changes are not deployed yet.
