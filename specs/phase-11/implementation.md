# Phase 11 implementation specification

Finalized 8 November 2026 for implementation. This document resolves the
proposals in phase-11.html. Product defaults below were selected under the
request to finalize the remaining decisions. Implementation can begin with
stubbed provider responses. Production enablement requires the project key and
confirmation of the project's applicable TheTVDB license.

## Product decisions

- Use TheTVDB through a replaceable MovieCatalogProvider application port.
- Use English metadata by default. Accept an optional ISO 639 language code
  supported by the adapter. Resolve each translated field through requested
  language, English, then original metadata. Return the effective title and
  synopsis languages. Preserve Unicode and accent distinctions in titles.
- Display canonical title, original title when different, release date/year,
  poster, expandable plain-text synopsis, runtime, genres and verified IMDb link.
  Render available values and a discreet linked provider attribution. Keep
  trailers, cast and numeric ratings outside this phase's display scope.
- Reuse the compact TV details layout, accessible poster enlargement, larger
  autocomplete images and timeline date formatter. Keep Table, Board and By year.
- Preserve MOVIE statuses TO_BE_SEEN and SEEN and the existing repeat completion
  history and local date/time editor. Existing completion service owns changes
  to history, including creation with an initial SEEN status.
- Require explicit search-result selection. Existing manual movies have a
  Find movie match action. Retain their original title as an editable alias when
  it differs from the canonical title. Matching never merges existing entities.
- Provide Change match and Return to manual actions with confirmation. On return
  to manual, prefill the effective alias, otherwise the current canonical title.
  The user can edit that title before submitting. A manual duplicate conflict
  keeps the existing match intact and permits retry with another title.
- Matching, changing match and returning to manual preserve entity ID, creation
  time, status, completions and article links. Show a separate optional alias
  while matched. Whitespace-only aliases clear to null. Alias length follows
  the existing entity title limit. Changing match preserves the alias.
- Provider refresh never changes personal status, history or creation time.
  Canonical title changes propagate through effective list/detail/search titles
  without creating a personal edit or incrementing the entity's version.

## API contract

All routes use existing authentication, ownership and Spring error JSON with
`reason`. UUIDs are Know catalog/entity identifiers. Versions are nonnegative
integers. Missing required versions or fields produce 400. Wrong owner and
missing owned resources produce the same 404. Strings are bounded using existing
title limits and query length 200. Unknown language codes produce 400.

| Method | Route | Input | Success |
| --- | --- | --- | --- |
| GET | /api/v1/movie-catalog/settings | none | 200 Settings |
| GET | /api/v1/movie-catalog/search | query, optional language=eng, optional cursor | 200 SearchPage |
| GET | /api/v1/movie-catalog/{catalogId} | optional language=eng | 200 CatalogDetails |
| POST | /api/v1/movie-catalog/entities | CreateMovie | 201 MovieDetails and Location /api/v1/entities/{id} |
| PUT | /api/v1/entities/{id}/movie | LinkMovie | 200 MovieDetails |
| GET | /api/v1/entities/{id}/movie | none | 200 MovieDetails |
| PATCH | /api/v1/entities/{id}/movie | AliasChange | 200 MovieDetails |
| POST | /api/v1/entities/{id}/movie/change-match | ChangeMatch | 200 MovieDetails |
| POST | /api/v1/entities/{id}/movie/unmatch | Unmatch | 200 existing EntityResponse |

Existing generic entity APIs continue to support manual movies, statuses,
completions, article associations and deletion. A matched entity rejects generic
title edits with 409 MOVIE_TITLE_READ_ONLY. Manual entity details have no movie
mapping and the movie subresource returns 404. Generic lists/details add nullable
movie summary data and an effective canonical title while preserving existing
response fields. Search matches effective canonical title, original manual title
and alias. Sorting retains existing semantics.

```typescript
type Settings = {
  enabled: boolean;
  minimumCharacters: number; // default 2
  debounceMs: number; // default 300
  pageSize: number; // default 8
  defaultLanguage: string; // eng
};
type Attribution = { text: string; url: string };
type MetadataState = "FRESH" | "STALE" | "PENDING" | "FAILED" | "UNAVAILABLE";
type Metadata = {
  state: MetadataState;
  fetchedAt: string | null; // UTC instant
  nextAttemptAt: string | null;
  failureReason: string | null; // stable sanitized code
};
type Candidate = {
  catalogId: string;
  title: string;
  year: number | null;
  posterUrl: string | null;
};
type SearchPage = {
  items: Candidate[];
  nextCursor: string | null;
  stale: boolean;
  attribution: Attribution;
};
type Movie = {
  catalogId: string;
  title: string;
  originalTitle: string | null;
  releaseDate: string | null; // YYYY-MM-DD, only when complete
  releaseYear: number | null;
  synopsis: string | null; // plain text
  runtimeMinutes: number | null;
  genres: string[];
  posterUrl: string | null;
  originalPosterUrl: string | null;
  imdbUrl: string | null;
  titleLanguage: string | null;
  synopsisLanguage: string | null;
  attribution: Attribution;
};
type CatalogDetails = { movie: Movie | null; metadata: Metadata };
type MovieDetails = CatalogDetails & {
  entityId: string;
  entityVersion: number;
  version: number; // mapping version
  catalogId: string;
  alias: string | null;
  language: string;
  status: "TO_BE_SEEN" | "SEEN";
  createdAt: string;
};
type CreateMovie = {
  catalogId: string;
  language?: string;
  status?: "TO_BE_SEEN" | "SEEN";
};
type LinkMovie = { entityVersion: number; catalogId: string; language?: string };
type AliasChange = { entityVersion: number; version: number; alias: string | null };
type ChangeMatch = {
  entityVersion: number;
  version: number;
  catalogId: string;
  language?: string;
  confirmed: true;
};
type Unmatch = {
  entityVersion: number;
  version: number;
  title: string;
  confirmed: true;
};
```

AliasChange requires the alias property even when clearing. Retain partial date
precision through releaseYear and nullable releaseDate. Return null for unknown
runtime. Preserve authoritative empty fields after a successful full refresh.
Transient fetch failure retains the last good metadata. Use HTTPS image URLs
from validated provider hosts and construct IMDb URLs from verified `tt` IDs.

Search materializes minimal shared catalog records and external references for
the returned candidates. Fetch full detail only for a selected candidate. Opaque
cursors are signed/scoped to query, language, configured provider and page size,
expire after 24 hours and have a bounded encoded length. Invalid/expired cursors
produce 400 INVALID_CURSOR. Request eight results, retaining any provider page
remainder in the cached page cursor. Preserve provider relevance ordering and
deduplicate repeated provider identities. A successful empty result is 200.

Create/link/change require verified full catalog details fetched within the
30-day stale allowance. Fetch a missing/older record outside the transaction
before mutation. On unavailable verification return 503 and preserve the prior
state. A known retired record returns 409 MOVIE_UNAVAILABLE. Existing mappings
remain readable with cached metadata and an UNAVAILABLE state.

Mutation transactions take the existing account lock, validate ownership/type,
check all supplied versions, then check duplicate identity before writes. Create
inserts entity and mapping atomically. Linking an already matched entity to a
different movie returns 409 MOVIE_MAPPING_CONFLICT. Repeating a link to the same
movie/language with current versions is a no-op. Change to the same identity and
language is also a no-op. Other successful link/alias/change/unmatch mutations
increment entityVersion once and mapping version once when a mapping remains.
Stale retries return 409 VERSION_CONFLICT. Repeating create for a movie already
in the account returns 409 MOVIE_ALREADY_ADDED. Responses may identify only the
same account's conflicting entity. Clients can navigate to it after confirmation.

Other errors use 400 INVALID_REQUEST, 409 ENTITY_ALREADY_EXISTS for manual title
duplicates and existing hash-collision handling. Cold-cache provider errors use
503 MOVIE_PROVIDER_UNAVAILABLE or MOVIE_PROVIDER_RATE_LIMITED with Retry-After
when known. Feature disabled returns settings.enabled=false and 503
MOVIE_CATALOG_DISABLED for catalog search/new matching. Existing cached details,
unmatch, alias, entity deletion and personal history remain usable. Failure
reasons never include credentials or raw upstream bodies.

## Database and migration contract

Add the next unused Flyway migration at implementation time. Current maximum is
V10. Match the existing UUID, TIMESTAMPTZ, JSONB and optimistic-version conventions.

- movie_catalog has UUID id, original_title, original_language, release_date,
  release_year, runtime_minutes, genres, poster_url, original_poster_url,
  metadata_provider, fetched_at, updated_at, version and availability state.
  A successful detail fetch validates a nonblank canonical title. Search-created
  stubs retain a title/year/poster and a null full-detail fetched_at.
- movie_catalog_localizations uses (movie_id, language) as primary key and stores
  title, synopsis, effective field languages and fetched_at. Keep synopsis plain
  text throughout contracts and storage. Enforce positive known runtimes,
  nonnegative versions and date/year consistency where both are supplied.
- movie_external_references has UUID id, movie_id FK, provider, external_id and
  verified_at. UNIQUE(provider, external_id). Keep reference strings extensible.
- entities gains nullable movie_catalog_id FK and a constraint requiring type
  MOVIE when set. Replace the manual title unique index with the predicate
  tvmaze_id IS NULL AND movie_catalog_id IS NULL. Add UNIQUE(account_id,
  movie_catalog_id) for mapped movies. This allows same-title remakes while
  retaining exact manual title duplicate behavior and existing TV identities.
- entity_movies stores account_id, entity_id, movie_id, alias, language,
  created_at, updated_at and version. Composite owner/entity FK and unique owner/
  movie constraint protect isolation. A composite FK from (account_id, entity_id,
  movie_id) to an entities unique triple (account_id,id,movie_catalog_id) ensures
  both representations agree. Defer this FK until commit so mapping replacement
  and unmatch can update atomically. Match V8's mapping/store patterns.
- movie_search_cache is keyed by provider, normalized_query, language and page,
  with JSON result, fetched_at, expires_at, lease_token and lease_until. Query
  normalization trims and collapses whitespace and uses Locale.ROOT case folding.
- movie_catalog_jobs stores UUID job ID, movie_id, target_provider, language,
  job kind, available_at, attempt count, status, claim token, lease expiry,
  requested revision and sanitized last error. A partial unique index coalesces
  equivalent active jobs. Index pending available_at and expired leases.
- movie_provider_state uses provider plus credential/quota scope as its key and
  stores shared cooldown and request-budget state. Store credentials in existing
  secret configuration mechanisms.

Existing rows remain manual. Store canonical metadata separately from original
manual entity title. Reuse an effective-title projection for lists, details and
article-linked entity labels, including localization fallback. Avoid rewriting
entity titles during catalog refresh. Returning to manual validates and writes
the submitted title/hash while removing both mapping references atomically.

Deletion extends Phase 10's account-scoped transaction to remove entity_movies
before the entity, retaining the existing history/link/connection cleanup.
Retain shared catalog rows and external references after personal deletion.
Cache cleanup removes expired search rows in batches of 200 and completed jobs
after seven days. Keep full shared catalog cleanup outside this phase.

## Provider adapter and operations

Use the official v4 schema as the adapter reference. Login uses POST /login with
the project apikey. Use movie-only /search and /movies/{id}/extended and request
translations on demand through the documented movie translation operation.
Validate exact paths and response DTOs against a pinned schema revision when
implementing the adapter. Keep fixtures representing that revision. Provider
response envelopes and pagination stay in infrastructure. MapStruct performs
DTO mapping. ArchUnit enforces feature boundaries.

Token initialization is lazy. Share concurrent login within a pod, retain tokens
only in memory and retry one login after 401. A second 401 pauses provider work
until a configurable five-minute retry and surfaces configuration failure.
Redact tokens, API keys and query strings from logs. User reading/viewing history
and article contents stay within Know. Provider calls carry search terms,
language and public catalog identifiers.

Configuration lives under know.movies with enabled=false as the default,
provider=thetvdb and language=eng. Backend production enabling requires a
project-appropriate key, its license review and a configured request budget.
For fixture development use a finite budget of one request/second, burst one,
shared across pods. A single provider call consumes one token including login
and translations. Live enabling must confirm that budget against granted terms.
Use a two-second connect timeout, ten-second total request timeout, two concurrent
calls per pod and a two-MiB decoded response cap, all configurable.

Retain approved search settings and TTLs from phase-11.html. Freshness for movie
details is per requested language. Fresh data returns immediately. Stale data
within 30 days returns immediately and queues refresh. Beyond 30 days, catalog
GET returns movie=null with PENDING or FAILED while background retrieval runs.
Existing entity views keep their effective title, alias and personal data while
metadata is pending. Known removal retains the last good snapshot marked
UNAVAILABLE and disables creating new mappings to it.

Claim jobs with expiring leases and token-fence publishing. Keep HTTP outside
database transactions. Default lease is 60 seconds, worker tick five seconds,
three transient retries after 30 seconds, two minutes and ten minutes with jitter.
Honor later Retry-After dates in shared cooldown. Bound queued work and do not
extend startup's save-ready path. Refresh mapped stale records in bounded batches
and coalesce reads with scheduled work. Recheck active provider, requested
revision and claim token before publishing. Failed requests never become cached
successful empty searches. After retries, mark FAILED and permit a later stale
read to enqueue again after a configurable one-hour failure cooldown.

Provider replacement preserves Know IDs. Implement and test a second fake adapter
and remapping jobs that attach verified references such as IMDb IDs. Ambiguous
or conflicting references remain unresolved with diagnostic codes. Apply fresh
metadata only after verified remapping. Retain the previous adapter/reference
for rollback. Configuration selects an adapter, while migration jobs establish
identity. Scope this phase's user UI to initial integration and diagnostic
states. Operator-driven replacement uses the tested application service.

## UI and acceptance

Autocomplete supports loading, empty, error, stale, disabled, keyboard navigation
and cancellation of outdated responses. Scroll the active option into view and
keep the dropdown within mobile viewports. Show attribution in the suggestions
and details. Image preview supports Escape, close button, focus containment,
focus restoration, meaningful alt text and a missing-image placeholder.

Find/change/unmatch show pending and recoverable errors, preserve entered alias
and title after failures, and refresh entity versions after 409. Deletion uses
existing confirmation and success navigation. Effective titles work in linked
article views and connection reasons. Verify older manual and TV data coexist.

Implementation order is migration and store, neutral provider port and fixture
adapter, TheTVDB adapter, application transactions and API, springdoc export and
both generated clients, then webapp. Review each checkpoint through code and
verification evidence. The finalization request authorizes these design defaults
without additional design approval gates.

Required checks include unit/ArchUnit tests and HTTP-to-Testcontainers PostgreSQL
integration tests for atomic create, same-title remakes, concurrent duplicate
addition, generic manual entry, initial SEEN completion, cross-account isolation,
version conflicts, aliases, unmatch title conflicts, deletion, preserved history
and article links, live catalog title changes, translated-field fallback, partial
dates, 401 renewal, 429 cooldown, timeout, malformed/oversized payloads, failed
search caching, stale reads, retired records, token-fenced refresh and restart
recovery. Test adapter replacement with retained UUIDs and ambiguous identifiers.

Webapp checks cover the normal entry flow, keyboard behavior, stale autocomplete
responses, same-title years, alias clearing, manual fallback, date picker,
poster enlargement, attribution, failures and responsive views at 1600, 1024,
390 and 320 pixels. Run lint, format, type check, build and relevant tests, plus
contract drift checks for backend, webapp and extension. Routine tests use
fixtures. A live smoke test is a release check after credentials are configured.

## Implementation readiness and release dependencies

Product and internal contract decisions are finalized in this document. During
implementation export executable OpenAPI from controllers and keep it consistent
with these shapes. Select the next migration number and pin the upstream schema
commit against the implementation branch before writing migrations and fixtures.

The remaining external release facts are the supplied project API key, applicable
license tier/terms and permitted request budget. Record them privately through
existing secret configuration and deployment processes. TheTVDB's public page
currently lists free attribution-based access below $50,000 annual company
revenue and includes parent-company revenue. Its terms must fit the actual
project. Display linked attribution wherever movie metadata is viewed.

Sources checked 8 November 2026

- https://www.thetvdb.com/api-information
- https://github.com/thetvdb/v4-api
- https://raw.githubusercontent.com/thetvdb/v4-api/main/docs/swagger.yml
