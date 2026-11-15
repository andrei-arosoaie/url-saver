# Phase 14 - Share a link to Know

Status: planned. This phase extends the Capacitor Android application and backend so a user can share a link from another application, choose a category, and save it to their Know timeline. The phase document and interactive mock do not implement server writes.

## User experience

1. In any Android application that offers standard URL/text sharing, choose Share, then Know. Examples include browsers, news apps and video apps. Know cannot add a share action to another app that does not expose one, or recover a URL the sender does not supply.
2. Know opens **Save to Know**, using the existing web application logo, colors, fonts, fields and buttons. The same flow works on phones and tablets.
3. Show the URL, an editable title and a required category selector. Populate categories from the authenticated account's existing category API, including enabled custom categories. Preselect Other; let the user change it before saving. Display the current account. If signed out, retain the draft, complete Phase 13's native browser sign-in, then load that account's categories before permitting Save.
4. Save explicitly. Disable repeated submission while waiting. Confirm **Saved to Timeline** only after the server confirms the saved article ID and category.
5. **View in Timeline** opens that article using its stable ID in the shared Timeline interface. Clear any category/search filter hiding the new article, invalidate/refetch the timeline and category counts, and show the saved category. A newly created article uses the server's normal save timestamp and newest-first order. The user can instead return to the original application using Android Back.

The first implementation saves URL and title only. Receiving a link does not grant access to another application's DOM, cookies, extracted article HTML or screenshot. Label this accurately as a link-only save. Fetching full page content or creating screenshots is outside this phase.

## Category behavior

- Use stable category keys, never display names, in requests. Renaming a category does not change associations.
- Only enabled categories belonging to the signed-in account can be selected. Include the existing Other category and current custom categories, not a hardcoded eight-category list.
- Show loading/error states for the category catalog. Do not silently substitute Other when a chosen category is unavailable.
- The selected category is a deliberate user choice, including a confirmed Other selection, and must be protected from later automatic classification.
- If the category is disabled/deleted after the catalog loads, retain the draft, refresh categories and ask the user to choose an available category before retrying.
- Changing accounts invalidates the category catalog and previous selection. Retain the URL/title only with an explicit account review; do not save an earlier account's pending draft automatically to a different account.
- Cancel causes no server mutation. Creating, editing or disabling categories remains in the existing Categories interface; it is not added to the share form.

## Current backend audit

`POST /api/v1/articles` already supports link-only saving with URL/title and optional content. `SaveArticleRequest` and the `SaveArticle` command contain only those three fields. `ArticleCommands.save` locks Other and `JpaArticleWriter.insertIfAbsent` uses the existing default-category insert path. The current contract therefore cannot atomically create a link in a caller-selected category.

Do not implement Save followed by a metadata PATCH for new links: that can leave a partially saved article in the wrong category and exposes an identification race. Extend the existing create transaction instead. No new mobile-only save endpoint is needed.

## Proposed backward-compatible API change

Add optional `category` to `SaveArticleRequest` / `SaveArticle` and the generated OpenAPI schema. Existing web/Firefox clients omitting it retain their current behavior. The Android share form submits its selected category key.

```http
POST /api/v1/articles
Authorization: Bearer <current account access token>
Content-Type: application/json

{
  "url": "https://example.com/postgres-indexes",
  "title": "PostgreSQL indexes, explained",
  "category": "TECHNOLOGY"
}
```

The example category is illustrative; actual keys come from the account catalog. `content` remains absent for a link-only save.

### New article transaction

1. Resolve the current authenticated account and existing default categories.
2. Validate URL/title using existing size, scheme and exact-URL policies. Reject an explicitly blank/null/invalid category instead of quietly falling back. Distinguish omission from an invalid supplied value at the presentation boundary.
3. For a supplied category, use the existing account-owned enabled-category lookup/locking rules. Preserve the established lock order when coordinating category mutation and article insertion. If omitted, preserve existing Other behavior.
4. Insert the article in the selected category and record USER category provenance when supplied, in the same transaction. Audit the insert query, persistence mapper, provenance protections and event listeners; do not merely add a DTO field that the database ignores.
5. Publish the normal captured event with the selected category/provenance already in place. Roll back both article and category assignment on failure. Do not make network/provider calls inside this transaction.
6. Return the normal save response with authoritative article ID, category and version. On CREATED, the timestamp is server-now; on an existing article, preserve its original save time.

Existing category/provenance columns may suffice; inspect the schema before adding a migration. If a migration is needed, retain existing records, defaults and associations. Export OpenAPI and regenerate web/Firefox types. Do not change account authorization or relax CSRF/origin checks to support Android.

### Duplicates and failures

Preserve exact-URL, account-scoped duplicate semantics and CREATED / ENRICHED / UNCHANGED outcomes. A new category parameter must not silently recategorize or replace an existing record. Existing content and title remain protected by current rules.

For a share that resolves to an existing article, show **Already in your timeline**, its current category, and **View existing**. If the requested category differs, offer a separate explicit **Change category** confirmation using the existing versioned metadata PATCH. Reload/reconfirm on a version conflict; never retry an outdated write automatically. A successful explicit category change does not move an existing article to the top of the timeline by rewriting its save timestamp.

Return the repository's existing category validation/ownership error conventions; document any additional reason in OpenAPI and the Android adapter rather than inventing a parallel error family. No invalid category response should reveal another account's category or article. A lost response says **Save not confirmed** and retains the draft. Before another save, offer a deliberate retry or reload/reconciliation through existing exact-URL handling. Never claim a timeout proves nothing was saved. No unattended retry queue is added in this phase.

## Android / Capacitor architecture

Active source: `android/capacitor/`, native project: `android/capacitor/native/`. The archived Compose prototype stays unchanged in `android/kotlin/`.

- Register an Android ACTION_SEND receiver for `text/plain` in the app manifest. Receive both a cold-launch intent and `onNewIntent` when Know is already running. This is receiving a share; the outbound Capacitor Share plugin alone does not implement it.
- Extract `EXTRA_TEXT` and, when present, `EXTRA_SUBJECT` in native Kotlin/Java. Validate payload length and type. Parse an absolute HTTP/HTTPS URL from text without silently dropping query parameters/fragments or applying URL normalization that changes the server's exact match.
- A single URL prepopulates the form. If multiple URLs are present, let the user select one; batch saving is not part of the phase. Unsupported schemes, empty payloads or text with no valid URL produce a clear error with no request to Know.
- Treat shared titles/URLs as untrusted text. Do not inject them as HTML or fetch arbitrary links for title discovery. Use a supplied usable title, otherwise prefill the URL as an editable title; the existing nonblank/length validation still applies.
- Add a small native share-inbox plugin with methods to read and explicitly consume/cancel a pending draft, plus notifications for new drafts while the UI is ready. Retain a draft until the web listener acknowledges it; a cold-start event must not be lost before React initializes. Use a delivery ID to prevent lifecycle redelivery from submitting twice; do not deduplicate distinct deliberate shares solely by URL.
- Persist the minimal pending URL/title/delivery state in app-private storage across process recreation and sign-in. Exclude it from backup; bound size and retention, clear on cancel/confirmed handling and follow account isolation rules. Pending means awaiting user action, not a background write queue.
- A new share arriving while another draft has unsaved edits prompts the user to review/replace or retain it; it must not silently overwrite the current form. App foreground/resume alone never triggers a server write.
- The React share form reuses existing UI styling, category APIs, error patterns and mobile authenticated transport from Phase 13. Require the secure native session; never reuse the Firefox client ID, embed a client secret or rely on web-only cookie assumptions.
- Hardware Back dismisses dialogs first, then asks before discarding an edited share draft, then returns to the sender/root according to normal Android task behavior. Test both cold and warm task entry.
- Native bridge access remains limited to the trusted bundled app. Saved article iframes and arbitrary remote content must not access pending shares, credentials or native methods.

## States and interactive mock

Required states: receiving, signed out, authenticating, loading categories, editing, validation error, saving, saved, duplicate, unavailable category, offline, unconfirmed response and account change. Preserve URL/title/category drafts across recoverable failures. Success uses the shared Timeline rather than a separate mobile library.

`phase-14.html#preview` contains an interactive phone/tablet mock using the current web form, button, header, theme and timeline styles. It supports category selection, a simulated Save, View in Timeline, and a duplicate sample. It is self-contained and works when the phase page is opened from disk. All mock data is fictional and no network save is performed. The mock is not evidence that Android share reception or backend category-on-create support already exists.

## Delivery checkpoints

1. Complete Phase 13 connected Android sign-in and API transport, or deliver those prerequisites before enabling Phase 14 Save. Approve the visible share/category/timeline interaction using the existing application design.
2. Implement the optional category-on-create backend contract and atomic persistence/provenance changes. Preserve omitted-category behavior for existing clients. Export OpenAPI and regenerate dependent types.
3. Implement and test the Android receiver, durable pending-draft handshake and cold/warm launch paths. Add the React form with enabled account categories and explicit Save/Cancel.
4. Connect authenticated save, duplicate review, explicit versioned recategorization and authoritative timeline navigation/refresh. Include category counts and filters in invalidation.
5. Verify complete HTTP/database and phone/tablet sharing flows. Roll out only after the acceptance gates pass; signing, distribution and production configuration follow their existing authorized workflow.

## Acceptance and verification

Backend: a newly shared URL and its selected category commit together; supplied categories get USER provenance; omitted category preserves legacy behavior; unknown/disabled/foreign categories fail without inserting a record; concurrent disable/delete and duplicate saves are safe; cross-account access fails; existing content/history/associations survive; existing URL is not silently recategorized; versioned category changes conflict correctly. Verify captured-event/identification handling cannot overwrite the manual category. Use disposable PostgreSQL, not production fixtures.

Android: share a link from representative browser/news/video applications on a real phone and tablet, both closed and running. Cover missing title, surrounding text, invalid/oversized payloads, multiple links, custom category names, no categories loaded, category disabled during editing, sign-in cancellation/return, account switch, repeated intent delivery, concurrent shares, rotation, process death, offline, response loss and Back/Cancel. Confirm exactly one creation and no automatic retry after an unconfirmed response.

Full flow: Share -> choose a non-Other category -> Save -> View in Timeline. Assert the real server response and database row use that category, the timeline shows the correct category and article, the list is refetched without a hiding filter, and web/Android show the same record. Category color/name comes from the catalog. Run accessibility, large-text, dark/light and narrow/tablet layouts. Inspect mock rendering in addition to testing interactions.

## References

- [Android: receive data from other apps](https://developer.android.com/develop/ui/compose/sharing/receive)
- [Capacitor native plugins](https://capacitorjs.com/docs/plugins)
- Repository sources: `server/openapi/openapi.yaml`, `SaveArticleRequest.java`, `SaveArticle.java`, `ArticleCommands.java`, `JpaArticleWriter.java`, category application services, `webapp/src/timeline/TimelineList.tsx`, `webapp/src/categories/CategoriesPage.module.css`.
