# Phase 2 — Timeline and entity lists

Status: approved product scope; concrete schema and API contracts are drafted for technical review. Mockups demonstrate behavior, not production implementation.

Visible pages: [Phase 2 specification](phase-2.html), [architecture and contract overlays](phase-2-architecture.html), [implementation guide](phase-2-implementation.html).

Review artifacts: [SQL schema](specs/phase-2/schema.sql), [OpenAPI YAML](specs/phase-2/openapi-draft.yaml), [operation details](specs/phase-2/contracts.json).

## Delivery phases

1. **Functionality:** capture, save, list and read articles; preserve Phase 1 contracts.
2. **Timeline & Entities:** the approved timeline, entity lists and completion history described here.
3. **Extension retries:** retry server non-response every minute, up to five retries, with meaningful progress and final failure; see [Phase 3](phase-3.html).
4. **Screenshots & R2:** viewport screenshot capture and compressed file storage; see [Phase 4](phase-4.html).
5. **Category management:** create personal categories, disable defaults and remove personal categories; see [Phase 5](phase-5.html).
6. **Automatic Entity & Category Identification:** classify captured pages and identify entity names/types; see [Phase 6](phase-6.html).
7. **Article Connections & Semantic Similarity:** shared-entity relationships plus asynchronous embeddings and pgvector in Supabase PostgreSQL; see [Phase 7](phase-7.html).
8. **Authentication & Authorization:** Google identity, accounts, authorization, tokens and sessions.

These eight phases are visible as separate top-navigation groups on every page. Other enrichment and graph ideas remain outside this numbered delivery scope.

## Navigation and shared presentation

- Left navigation: Timeline and Entities. Desktop retains the sidebar; mobile uses compact navigation and full-screen record details.
- Blue accent, light/dark/system themes selected with minimal icons; follow the OS in system mode.
- Search is visible. Timeline category filters affect timeline entries, not the open reader.
- Timeline and Entities mockups: [timeline](web-mockups.html), [entities](entities-mockups.html).

## Timeline

- Newest captured entries first; creation time is the capture save timestamp, not the publication date or entity completion date.
- Connected colored circles on a gently curved spine, with clickable title cards and detail/reader view.
- Categories and colors: Architecture (blue), Development (purple), AI (cyan), Other (amber), Books (teal), Movies (coral), TV shows (pink), Games (green). An All categories option is available here.
- Creation label above time on each record. Relative to the viewer's local calendar: Today; Yesterday; weekday for 2–6 days ago; day + month for older entries in the current year; day + month + year for earlier years. Time is always below. Exact timestamp remains available in record details.
- Book, movie, TV-show and game records show an identified entity name when available. Category and identified entity name are manually editable. Automatic extraction of the name/category is Phase 6.
- A compact raised-white action at the top-right of record details uses the selected list-plus icon (A4) and Add label. Once added, it changes to list-minus (R4) and Remove. No large action panel.
- Add sends the identified entity to the corresponding list. Remove unlinks only this captured page from its entity. The entity, its list membership, completion history and other linked pages remain unchanged.

## Viewport screenshots

Moved to [Phase 6 — Screenshots & R2 storage](phase-6.html).

## Entity lists

- Backend entity types use a Java enum: BOOK, MOVIE, TV_SHOW and GAME. Persist stable enum names as strings, never ordinal numbers. Article categories remain data-driven records, separate from entity types.


Exactly one type is selected at all times; there is no All types selection.

| Type | Allowed statuses | Completion date label | Year filter |
| --- | --- | --- | --- |
| Books | To read / Reading / Read | Read on | Read year |
| Movies | To be seen / Seen | Seen on | Seen year |
| TV shows | To be seen / Seen | Seen on | Seen year |
| Games | To play / Playing / Finished | Finished on | Finished year |

- Every entity has a title/name, type, status and creation timestamp. Completed entities expose completion history, preserving repeated reads, viewings and playthroughs. Each completion has its own exact UTC timestamp.
- Manual Add book / Add movie / Add TV show / Add game is available without a captured page.
- Titles and statuses are editable. Creation and each completion are stored as exact UTC timestamps and displayed independently. Entering Read, Seen or Finished records a completion automatically, using a supplied exact timestamp or the server current UTC instant when omitted. Do not prompt for the time. Another completion can be recorded through explicit Add completion.
- Table, Board and By year are switchable views of the same selected type, search and year filters.
- Table compares titles, statuses, creation dates and completion dates; selecting a row opens editable details.
- Board has one column per allowed status: three for books/games, two for movies/TV shows.
- By year uses completion-history dates, not creation year. An entity completed in different years can appear in each corresponding year. Unfinished entities remain accessible in Table and Board.
- Selecting a completion year shows entities with completion events inside that UTC calendar year. Show one row per entity with its completion count for the selected year. All years removes only the year restriction, not the required type selection.
- Linked captured pages are reachable from entity details. Unlinked manually entered entities show no fabricated source.

## Architecture direction

Keep the approved com.know feature layout: presentation, application and infrastructure. Add an entities feature; preserve identity/accounts separation.

- articles owns saved page content, exact-URL identity and capture creation timestamps; the timeline reads these records.
- entities owns manually or capture-derived list items, titles, type-specific status and completion information.
- The article/entity association must preserve both records' ownership. Multiple captured pages can link to one shared entity. Preserve its stable ID when titles change.
- Use explicit application contracts for cross-feature operations. Do not introduce feature cycles or expose JPA entities to presentation/application records.
- Existing PostgreSQL/Flyway, Spring Data JPA, MapStruct, Java records and constructor-injection conventions still apply.
- Backend contracts, schema and association constraints must be reviewed before implementation. Mock sample IDs, browser storage and URL-fragment state are not production API or identity decisions.

## Required verification

- Unit coverage for relative dates, including midnight, year boundaries and daylight-saving transitions in the selected display timezone.
- UI coverage for one selected type, all 12 type/view combinations, category/search filtering, manual entry, title edits, applicable statuses, year grouping and desktop/mobile navigation.
- Real HTTP → application → PostgreSQL integration flows: captured entry → add entity → retrieve/edit/status change → completion-year query; manual entity creation without an article; linked-page navigation; removal under the agreed policy.
- Cross-account isolation for entities and associations; forbidden status/type combinations rejected without partial writes; creation timestamps remain separate from completion timestamps. Verify omitted completion time uses an injected server clock, supplied UTC times are honored, completion/status changes are atomic, unchanged completed status does not append history, and edits/removals recompute UTC-year counts.
- Integration classes end in IT, regular tests in Test. Reuse the shared Spring context/container, reset fixtures, and retain the mandatory full-flow checks.
- Mock DOM checks establish scripted behavior only. Production browser/device and backend verification are separate evidence.

## Confirmed entity policies

| Area | Approved behavior |
| --- | --- |
| Shared identity | Several captured pages can link to one shared entity, and one captured page may link to multiple entities: a many-to-many association. |
| Completion history | Preserve repeated completions as separate records. Explicit Add completion records another event; do not overwrite earlier events. |
| Completion precision | Exact UTC timestamp, e.g. 2026-10-25T18:30:00Z. Use a supplied instant, otherwise infer the server current UTC time automatically; no time prompt or permission step. |
| Completion and status | Entering Read / Seen / Finished records completion automatically. Adding any completion, including a past one, sets the corresponding completed status. |
| Year grouping | UTC year boundaries. One entity row per year with a count of its completions in that year. |
| History corrections | Edit/remove individual completion events without changing current entity status. Recompute history-derived dates and counts only. |
| Timeline Remove | Unlink this page only. Keep the entity, list membership, all completion history and other page links. |
| Metadata editing | Category and identified entity name can be edited manually while automatic extraction belongs to Phase 6. Existing entity links stay until explicitly replaced; editing text never silently relinks or unlinks. |
| Manual duplicates | Reject exact-text duplicates within the same selected type. Case and whitespace are significant: Dune and dune or a title with outer spaces are different. Do not trim/case-fold titles for identity. |

### Data consequences

- Entity records have stable identity, owner, type, title, current status and creation time.
- Source associations are many-to-many, linking captured pages to shared entity records; completion events belong to the entity rather than a source page.
- Each completion event has its own identity and exact UTC completion timestamp. PostgreSQL timestamptz / Java Instant and UTC API timestamp strings follow the existing timestamp conventions. A single mutable finished-date column cannot represent history.
- Completion creation and setting the type-appropriate completed status occur in one transaction. Repeating an unchanged completed status is not a new completion; use Add completion for another event.
- UTC year filtering uses a half-open interval: 1 January 00:00:00Z inclusive through the next 1 January exclusive. Count events per entity within this interval. Display-timezone conversion must not change year membership.
- Editing a completion timestamp updates the affected UTC year groups and counts. Removing a history entry removes only that event, preserving the entity and other history.
- Unlinking a page must not cascade-delete the entity or its completion events.
- Duplicate checks must also hold under concurrent manual creation. Identity checks use the owner, selected type and exact unmodified title. Blank-title validation must not silently normalize stored titles.
- Add from another page reuses the identified shared entity rather than duplicating its history. Use the selected entity ID or the reviewed exact-title/type matching contract; do not invent fuzzy merging.

## Next contract review

The [dedicated architecture page](phase-2-architecture.html) now contains the concrete SQL design, owner-checked many-to-many ER diagram, and 17 complete operation contracts in clickable overlays. The [OpenAPI review draft](specs/phase-2/openapi-draft.yaml) describes required types, versions, filters and example payloads. Existing Phase 1 OpenAPI and migrations are unchanged.

Approved: history edits/deletions preserve current entity status; entity/completion writes use optimistic versions; exact-title SHA-256 uniqueness guard; cursor pagination with configurable initial default 20 and maximum 100 in application.yml. Final route and request/response contract review remains. Initial statuses and case-insensitive title search are approved. Adding a completion sets completed status as already approved.

The existing mockup uses one completion date and a simplified browser store; these are not the final history or identity implementation. Update its behavior alongside the reviewed contracts. Viewport screenshot capture and R2 storage belong to Phase 4; full-page capture remains outside scope.

## Extensible categories — Phase 2 foundation

- Categories are account-owned data records, not a fixed database/API enum. Seed Architecture, Development, AI, Other, Books, Movies, TV shows and Games with their current colors for each account.
- Each category has a stable key, display name, color, default/custom origin, enabled state, owner, creation timestamp and version. Articles reference a category belonging to the same account. Keep the existing predefined keys for compatibility; presentation labels are not identifiers.
- Phase 2 reads this catalog for filters, editing choices and node colors. Existing article records start in Other. New accounts receive the same initial set.
- Management controls (create custom, disable default, remove custom) belong to Phase 5. Existing-article handling on disable/removal needs review before that phase; never silently delete articles.
- Entity types BOOK / MOVIE / TV_SHOW / GAME remain separate from article categories and retain their agreed statuses.

## Approved consistency and pagination

- Entity and completion updates use versions; stale writes return 409 VERSION_CONFLICT, with no automatic overwrite.
- Exact-title uniqueness uses application-generated SHA-256 verified by PostgreSQL, scoped by owner and entity type. Compare full titles on hash matches.
- Cursor pagination uses initial default 20 and maximum 100; both are configurable through application.yml and mounted YAML overrides. Validate positive limits and default <= maximum.

## Approved initial statuses and search

Approved initial statuses: BOOK → TO_READ; MOVIE / TV_SHOW → TO_BE_SEEN; GAME → TO_PLAY. Entity search is a case-insensitive literal title substring. Search does not alter stored titles or exact-text duplicate identity.
