# Archived Kotlin design

Superseded by the Capacitor decision. Preserved as reference for the original prototype; the active design is specs/phase-13/design.md.

# Phase 13 — Native Android application

Status: proposed design, 8 November 2026. This phase designs a Kotlin Android client for phones and tablets; it does not claim an Android implementation or release exists.

## Scope and baseline

Deliver the functionality currently implemented by the React web application through the existing account-scoped backend. Use native Jetpack Compose screens, sharing server records, rules and identifiers with the web app. One application and one codebase serve both phones and tablets, including resized and split-screen windows.

The baseline is `webapp/src`, `server/openapi/openapi.yaml`, and the identity controllers, rather than older README/status summaries. In particular, entity deletion and TV progress are already implemented. Planned Phase 11 movie catalog integration and Phase 12 Stremio setup are dependencies only when those capabilities become implemented server/web features; they do not block current parity.

Scope matches the current online web app: no additional durable offline library, offline write queue or Share to Know flow is introduced in Phase 13. The Firefox extension remains responsible for browser DOM extraction and viewport capture. Reading its saved content and screenshots is included in Android parity.

## Navigation and visual design

Match the existing web app design exactly: use the Know layered blue logo, blue accent (#1660ec), white panels, pale-blue background (#f5f7fc), navy text (#17233d), existing light/dark tokens, serif content headings, curved timeline spine and colored nodes, category chips, existing selected states, borders, tables and detail panels. Translate these components to Kotlin/Compose; do not substitute a new Material visual theme. System/light/dark appearance remains identical to the web app. Theme preference is local DataStore state. Do not invent article excerpts absent from the list contract.

Non-negotiable acceptance criterion: the Android app has the same design and full implemented feature scope as the web app. No functionality is dropped or deferred merely because the client is native. Designs must be shown and visually verified at phone and tablet sizes before implementation is accepted.

Primary destinations: Timeline, Entities, Categories. Account/profile and appearance are available from the top app bar. Each destination preserves its own filters and scroll position. Stable article/entity IDs identify routes; URLs and titles never identify records. Back first dismisses a sheet/dialog, then returns from detail to list, then follows destination history. External article links open in a browser after explicit activation.

| Available window | Navigation | Content arrangement |
| --- | --- | --- |
| Compact, following the web app mobile breakpoint | Existing web app top navigation row: Timeline / Entities / Categories | Existing mobile header and search; timeline switches to reader on selection; preserve web editor/detail behavior |
| Intermediate windows | Existing responsive web navigation | Reproduce the current web breakpoint behavior; do not invent an Android layout |
| Wide tablet windows | Existing left sidebar navigation | Existing timeline/reader columns and entity collection/detail workspace with web proportions |

Use current window information and posture, not device names or orientation locks. Navigation placement follows the web app; no new bottom navigation or Material rail design. Avoid hinges and system/IME insets. Reader widths, typography and pane proportions follow the existing web CSS. Resizing preserves selection, scroll anchor, drafts and back behavior. A detail selection stays selected when the layout changes. On large screens an empty detail pane says “Select an article” or “Select an entity”.

Touch targets are at least 48 dp. Support TalkBack labels and headings, keyboard traversal, visible focus, 200% font scaling, reduced animation and non-color status labels. Destructive actions use labeled overflow actions and confirmation dialogs, never swipe-to-delete alone. Board movement also has an accessible status menu.

## Feature parity and API map

API paths below are under `/api/v1` except OAuth. Endpoint parameters, error bodies and version preconditions must come from the checked-in contract; this design does not redefine them.

| Screen/flow | Required behavior | Existing contract/source |
| --- | --- | --- |
| Sign-in/account | Google sign-in, connecting/error/session-expired states, profile name/picture, remove/restore picture, sign-out, version/revision | `/oauth2/*`; identity profile controller; `profile/ProfileMenu.tsx` |
| Timeline | Newest-first cursor pagination, category/search filters, clear filters, load more, refresh; return from detail retains position | `GET /articles`, `GET /categories` |
| Reader | Sanitized saved HTML, title/source/category, missing-content state, original source link; screenshot preview, zoom and refresh expired delivery URL | `GET /articles/{id}`, `GET /articles/{articleId}/screenshot` |
| Article actions | Explicit versioned metadata edits, confirmed permanent deletion, manual identification retry and provenance; explicit entity add/link/unlink | Article PATCH/DELETE; `/articles/{articleId}/identification`; `/articles/{articleId}/entities` |
| Connections | Distinguish shared-entity factual links from semantic suggestions; reasons, pagination, processing/paused/failure states, explicit retry and confirmed removal where supported | `/articles/{articleId}/connections` and its retry/removal routes |
| Entity collection | Exactly one type: books, movies, TV shows, games; Table / Board / By year; manual create, type statuses, search/filter options exposed by web contract | `/entities`, `/entities/years`; `EntitiesPage.tsx` |
| Entity detail | Edit title/status, linked source articles, link/unlink behavior, confirmed entity deletion with server-defined consequences | `/entities/{id}`, `/entities/{id}/articles`; `DeleteEntity.tsx` |
| Completion history | Server-now completion, repeated/historical completions, correction/removal; UTC year grouping and counts | `/entities/{id}/completions` and event routes |
| TV details | TVMaze search/link/create, change show, alias controls, season/episode browsing, episode watched state, viewing position, mark-seen, released-episode progress and catalog readiness | `/tvmaze/*`, `/entities/{id}/tv-show/*`; `TvDetails.tsx` |
| Categories | Counts linking to filtered timeline, custom create, rename/color edit, disable/re-enable, confirmed removal | `/categories`, `/categories/{key}` |

Table mode remains available on phones through a labeled horizontally scrollable table with its own viewport; reproduce the current web app mobile table presentation and overflow rules. Board mode scrolls columns within its viewport, not the whole screen. By year groups completion events in UTC, not creation dates. Books use To read / Reading / Read; movies use To be seen / Seen; TV uses To be seen / Seeing / Seen; games use To play / Playing / Finished.

Category keys and associations survive renames. Other stays enabled; in-use categories cannot be disabled/deleted; predefined categories cannot be deleted. Article deletion preserves entities/history. Unlink only removes the association. Entity deletion follows the implemented Phase 10 contract; do not substitute article-deletion behavior. Confirmation names the selected record and displays relevant consequences.

Completion timestamps round-trip microsecond UTC precision. Use `Instant` plus the API's original precise representation; never convert existing values through millisecond-only storage. Editing shows the timezone and explicitly converts user input to UTC.

## Screen state and consistency

Every collection has initial loading, empty library, empty filtered result, loaded, loading-more, recoverable error and session-expired states. Load-more failure retains existing rows. Detail screens clear the previous record when ID changes and cancel/ignore late responses. A failed request for B must never show A as B.

Use unidirectional state flow: Compose action → ViewModel → use case/repository → API → immutable StateFlow state. Lifecycle-aware collection stops unnecessary UI work. Poll identification/connections only while their screen is visible, obeying server intervals and terminal states. Reload on foreground entry when appropriate; bound pagination and request concurrency.

All writes carry the server's current version/precondition. Disable duplicate submission. Do not automatically replay writes after timeout, 401 refresh, process restart or a version conflict. If a response is lost, say “Change not confirmed”, reload authoritative state, and require another deliberate action and fresh confirmation before a destructive retry. A conflict retains the draft and offers reload/review; it never silently overwrites a newer record. Retry safe reads with bounded backoff. Serialize token refresh and prevent a refresh/logout race from restoring a signed-out session.

SavedStateHandle stores navigation IDs, filter values and suitable small drafts, not tokens or entire article HTML. Non-persisted drafts lost after process death receive no promise of recovery. Dirty editor dismissal asks whether to discard. Logout/account change clears in-memory records, image delivery metadata, drafts and pending requests so one account cannot see another account's library.

## Kotlin architecture

Proposed root: `android/`, with Gradle Kotlin DSL and version catalog. Proposed minimum Android API 26; compile/target SDK and exact stable dependency versions are selected and pinned during implementation against current distribution requirements. Enable Java time desugaring if the final minimum requires it.

Start with a small module graph: `:app`, `:core:model`, `:core:network`, `:core:auth`, `:core:designsystem`, and feature modules `:feature:timeline`, `:feature:entities`, `:feature:categories`, `:feature:account`. Avoid a module per screen. Features depend on repository contracts, not each other or provider SDKs.

Use Kotlin, Compose Material 3 + Adaptive, ViewModel, coroutines/Flow, Navigation Compose, Retrofit/OkHttp with a single configured JSON serializer, and dependency injection through Hilt. Use an image loader with account-aware cache policy for private screenshots/posters. Do not persist signed screenshot URLs; expiry/refresh rules come from the backend. No AI, TVMaze, ImageKit, Google client secrets or service credentials ship in the APK. All provider calls stay behind the existing server.

Generate transport models/client from OpenAPI only after evaluating generator output and nullability; otherwise use explicit DTOs verified against contract fixtures. Map DTOs into stable domain models, preserving IDs, timestamps, versions and unknown-enum handling. Extend API documentation to include identity contracts rather than pretending the article OpenAPI already includes them.

## Native authentication: required backend increment

Current identity code accepts only the web and Firefox client IDs. Android cannot simply reuse a web cookie session or Firefox client. Add a distinct public `know-android` client and exact allowlisted redirect(s) throughout authorize, exchange, refresh and logout validation. Reuse existing account ownership, JWT verification and rotating refresh-session semantics; add no mobile-only authorization bypass.

1. App generates a cryptographically random state and S256 PKCE verifier/challenge, retaining a short-lived pending transaction privately across browser return/process recreation.
2. Launch existing backend `/oauth2/authorize` in a system browser Custom Tab. Google callback remains on the backend. No embedded WebView sign-in and no client secret in the application.
3. Backend returns a short-lived one-use authorization code to a verified HTTPS Android App Link. Validate state, exact redirect, client binding and expiry before exchanging the code at `/oauth2/token`.
4. Keep access token in memory. Encrypt the rotating refresh token with a key held in Android Keystore; exclude tokens and pending authorization state from backup, logs, analytics and crash reports. Persist replacement token atomically; document recovery if refresh rotation is interrupted.
5. Revoke the current native session through logout. Clear local credentials immediately; if revocation cannot be confirmed, disclose that and require sign-in before more data access. On revoked/expired refresh, clear records and return to sign-in.

Verified App Links require a controlled HTTPS domain, hosted assetlinks.json with release signing certificate fingerprints, and debug/release separation. Package name, controlled domain and distribution/signing ownership remain release decisions. Do not invent a callback domain or assume the current Render hostname is configured for App Links. Native routes for opening article/entity links use verified ID-only deep links and always check server ownership after authentication.

## Saved article security

The native shell does not imply a native HTML renderer. Use a tightly isolated WebView solely for server-sanitized saved article HTML, preserving the current no-automatic-remote-resource policy. Disable JavaScript, JS bridges, file/content access, mixed content, popups and unsolicited downloads. Add a restrictive content security policy and intercept resource/navigation requests, denying network subresources by default. Render no bearer token or signed API content into page URLs. Explicit safe http/https source links open outside the reader; reject executable and local URI schemes. Test hostile HTML, redirects, images, forms, frames and CSS URLs. Signed screenshot retrieval uses the authenticated API and separate image UI, not arbitrary HTML fetches.

## Offline and sharing options

Baseline: network-required library access, explicit offline banner, retained in-memory content marked as potentially stale, disabled writes while disconnected, and deliberate reload when connectivity returns. No durable sync queue or background write replay. This makes no guarantee of reading after app restart offline.

Offline extensions and Android sharing are outside Phase 13 because they are outside the current web application scope.

## Implementation checkpoints

1. Freeze a web-to-Android acceptance checklist from the sources above. Use the current web scope without adding offline or sharing features. Decide minimum OS, application ID and release channel. Produce Compose previews for phone/tablet, large fonts, themes and all failure states.
2. Scaffold the Kotlin project, dependency pins, debug/release environments and CI. Implement adaptive navigation, theme and fake repository previews. No production fixture writes.
3. Implement the dedicated native OAuth client, verified redirects and secure token lifecycle. Test cross-client rejection, replay, malicious redirects, expired state, rotation, revocation and logout races against disposable infrastructure. Verify real browser return on devices.
4. Implement Timeline, reader, screenshot delivery, metadata, identification and connections. Verify stale responses, lost confirmations and version conflicts before enabling destructive actions.
5. Implement complete entity modes/history/deletion and TV flows; then categories and profile controls. Keep every API write's current server behavior. Demonstrate the parity checklist with the same fixtures as web.
6. Finish accessibility, process recreation, adaptive resizing, performance and release hardening. Build a signed test artifact only with configured signing; keep keys out of Git. Distribution/publication is a separate authorized action.

## Validation and release gates

Use meaningful repository/ViewModel tests for pagination/filter races, precise timestamps, unknown enums, optimistic-version failures and session isolation. MockWebServer covers serialization, auth refresh coordination, failed/lost writes and screenshot expiry. Backend integration tests cover the new native-client authorization boundaries while preserving web/Firefox behavior.

Compose instrumentation covers navigation/back, editor discard, confirmations, large text, accessible status changes and reader security. Test API 26 and current supported target, representative 360 dp phone, 800 dp tablet portrait and 1280 dp tablet landscape, narrow split-screen and fold/hinge posture. At least one physical phone and tablet verify browser sign-in, process recreation, keyboard/IME, screen reader and WebView behavior.

CI gates: Gradle formatting/static analysis, Android lint, unit tests, debug assembly and emulator instrumentation. Set startup/scroll benchmarks on agreed reference hardware before release; do not claim an unmeasured performance target. Review screenshot comparisons for all destinations in light/dark and no unintended overflow. Required deliverables: reviewed parity checklist, backend migration/config notes, reproducible APK/AAB, test evidence and release/signing documentation. No new library database schema is expected for baseline; native identity configuration/code is required.

## References

- [Android adaptive app guidance](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps): window-aware navigation and list/detail patterns.
- [RFC 8252 — OAuth for Native Apps](https://www.rfc-editor.org/rfc/rfc8252): external user-agent authorization and public native client security.
- Repository references: `webapp/src/App.tsx`, `webapp/src/entities/TvDetails.tsx`, `webapp/src/entities/DeleteEntity.tsx`, `webapp/src/profile/ProfileMenu.tsx`, `server/openapi/openapi.yaml`, `server/src/main/java/com/know/identity/infrastructure/IdentityController.java`, `IdentityProperties.java`.

## Reviewable phone and tablet designs

`phase-13.html#preview` embeds interactive phone and tablet mocks from a local design harness that imports the production `App`, `ProfileMenu` and CSS unchanged. These interactive mocks are the mandatory visual reference for the Kotlin implementation, not a claim that Kotlin is already implemented. The harness uses fictional in-memory data and blocks server requests. Preview-only write behavior is illustrative, not backend parity verification. The required implementation scope remains every flow in the parity table.

Source: `webapp/design/android-preview.tsx`. Build: run `design/build-android-preview.mjs` with Node from `webapp/`. Verification: `design/verify-android-preview.mjs` exercises phone 390?844 and tablet 1280?900, checks rendered route content, blue accent and page overflow, and captures ten screens per size. Evidence: `mockup-assets/android-preview/proof/verification.json`. Browser captures are verification evidence only, not the mock deliverable. Inspect the interactive phone and tablet mocks in addition to automated checks.

The mock artifact is self-contained HTML with inline production CSS and a classic bundled script. It opens directly from disk (file://) without a development server. Verification covers that exact file:// delivery path, including both embedded viewports and the screen selector.
