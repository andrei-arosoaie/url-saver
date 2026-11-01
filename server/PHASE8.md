# Phase 8: TV shows

The TV-show workflow lives inside Entities. Movie entry is unchanged.

## Identity and API

`POST /api/v1/tvmaze/entities` accepts `{tvmazeId,status?}` and creates the entity
and its mapping in one transaction. The server verifies the selected ID with
TVmaze and uses its canonical name. This is the atomic creation path used by
autocomplete. Manual creation remains `POST /api/v1/entities` and can be linked
later with `PUT /api/v1/entities/{id}/tv-show` and `{entityVersion,tvmazeId}`.
Search never silently links a result. Same-name shows with different IDs can
coexist; the same account cannot link the same provider show twice.

The remaining endpoints follow `specs/phase-8/contracts.json`; mutation responses
return the full updated TV details, including progress version, entity version
and resulting status. The exported OpenAPI contract is authoritative for JSON
types. Missing mappings and foreign owners both return 404. No account IDs are
accepted from clients.

Identified titles are locked. Alias updates are versioned and independent of
identity. Confirmed replacement preserves alias, article links and completion
history; it resets position, checkmarks, the seen watermark, the new-episode
indicator and status. Manual titles retain their exact-title duplicate rules.

## Catalog, caching and workers

Migration V8 stores public show/season/episode payloads separately from personal
mapping/progress. Payloads contain selected typed fields, including plain-text
synopses; executable provider HTML is never rendered. Provider episode IDs remain
stored when removed from a later catalog, preserving referenced personal data.

PostgreSQL search caching normalizes query case and redundant whitespace without
normalizing entity titles. It stores every candidate, while the UI initially
shows eight. Expiring, token-fenced refresh leases coalesce concurrent requests
and recover from crashes. Failures are never cached as successful empty results.
Stale results remain available within a bounded window and are labeled in the UI.
Cache cleanup deletes at most 200 rows per worker tick.

The durable catalog worker fetches outside database transactions and publishes
all seasons/episodes atomically under a lease fence. Refreshes coalesce by show
ID. Reads serve cached details immediately and enqueue stale catalogs. Scheduled
refreshes also run without opening the application. A separate evaluation of
known airing timestamps updates new-episode state without waiting for another
provider fetch. Account write locks serialize personal mutations across pods;
catalog locks prevent eligibility changes during progress writes.

TVmaze HTTP calls have bounded connection/request timeouts and a 4 MB response
limit. A shared provider permit and short cooldown coordinate instances.
HTTP 429/other HTTP failures honor Retry-After; this policy is independent of
Gemini. Only public search text and provider IDs are sent to TVmaze.

## Settings

All values use the `know.tvmaze` prefix in YAML. Defaults:

| Setting | Default |
| --- | --- |
| `base-url` | `https://api.tvmaze.com` |
| `minimum-characters` | 2 |
| `debounce-ms` | 300 |
| `page-size` | 8 |
| `catalog-ttl` | 24h |
| `positive-ttl` | 24h |
| `empty-ttl` | 15m |
| `stale-window` | 7d |
| `lease-duration` | 2m |
| `retry-delay` | 30s |
| `poll-interval` | 10000 milliseconds |
| `cache-row-limit` | 10000 |
| `scheduling-enabled` | true |

`GET /api/v1/tvmaze/settings` supplies the autocomplete settings to the client.
Integration tests disable scheduling and use a local provider fixture; no live
provider requests or production secrets are needed for verification.

## Browser verification

Build the webapp, then run `tvBrowserPreview` with the existing WSL Docker Gradle
toolchain and host networking (`TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`). It starts
a disposable database and the real backend on port 18096, seeded with a show
flagged for new episodes and a separate same-name remake. It uses the shared
integration fixtures and never loads local secrets or calls live providers.

Launch an isolated Firefox BiDi profile, then run `npm run test:browser:tv` in
`webapp`. Set `KNOW_IT_API_ORIGIN` to the localhost preview origin,
`KNOW_FIREFOX_BIDI_URL` to the isolated Firefox session WebSocket, and
`KNOW_BROWSER_PROOF_DIR` to a screenshot directory. The script checks Table and
three-column Board, saved position versus checkmarks, automatic Seeing, clearing
NEW, explicit completion, aliases, replacement confirmation/cancel, stable-ID
autocomplete and mobile layout. It refuses non-localhost API origins.

Verified on 2026-11-01: 44 backend unit/architecture tests, 72 PostgreSQL
integration tests, OpenAPI drift checks, 115 frontend tests, lint, formatting,
TypeScript and production build. The Firefox fixture flows passed at desktop
1360×1000 and mobile 390×844; screenshots were inspected for layout and overflow.
The HTTP adapter is tested against local TVmaze protocol responses. Live TVmaze
availability and physical-device testing are separate from these fixture checks.

## Viewing rules

Position and watched checkmarks are independent. Position saves only accept
confirmed aired regular episodes. Bulk marking requires explicit confirmation
and preserves existing checks when moving backward. Unknown airing timestamps
allow manual checkmarks; known future episodes remain blocked. Recording progress
changes To be seen to Seeing without creating a completion. Unchecking does not
move position or change status.

Explicit Mark show seen requires a complete successfully fetched catalog, at
least one aired regular episode and checks for all aired regular episodes. It
records the existing completion event and server-time watermark. Repeating it
while already Seen does not add history. Later confirmed aired, unwatched episodes
after that watermark change Seen to To be seen and set the green NEW indicator.
All currently aired checks clear the indicator without completing the show;
position-only changes do not clear it.

## Sources

Provider protocol: [TVmaze API](https://www.tvmaze.com/api).
Public metadata source: [TVmaze](https://www.tvmaze.com/), available under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). Provider
documentation is kept here; the application viewing controls contain no provider
branding.

## Refresh regression coverage

The worker's stale-catalog discovery query explicitly casts its scheduled time
parameter to `TIMESTAMPTZ`. PostgreSQL resolves an untyped parameter in a
`SELECT DISTINCT` projection as text, which otherwise prevents every refresh
tick from reaching queued jobs. Integration coverage runs the production worker
entry point from a pending mapping through catalog publication, season/episode
reads and a position save, then checks fresh-cache reuse and daily refresh.

The UI disables empty position controls while the catalog loads, keeps polling
through transient failures, and preserves an unsaved episode selection when
background metadata refreshes. Provider branding is kept out of the app UI.
