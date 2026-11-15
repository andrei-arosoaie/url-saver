# Phase 12: Stremio progress addon

Designed 8 November 2026. Planned, not implemented. Depends on Phase 8 TV progress,
Phase 9 account isolation, Phase 10 completion history and Phase 11 matched movies.
Earlier book catalog research is preserved in BOOK-INTEGRATION-RESEARCH.md and
deferred outside this phase.

## Product scope

Install a private Know addon to update existing, explicitly matched movies and TV
episodes from Stremio activity. Direction is Stremio → Know. Keep playback in
Stremio. No stream proxy, account scraping, Stremio password, historical import,
automatic entity creation or reverse write to Stremio is required.

The current official protocol supports native player and library events. Client
support must still be verified per version. Manifest installation does not prove
that a client emits events. No heartbeat, reliable delivery, globally unique event
ID, client timestamp or device/session identifier is guaranteed. Show the last
received sample rather than a live playback timer. Missing events cannot be
reconstructed.

## User experience

Settings → Integrations → Stremio offers Connect, explains sharing watch activity,
then displays Install in Stremio and Copy manifest URL. Only a signed-in Know
owner can create the connection. Show its private URL once, on creation or
rotation, with an explanation that anyone holding it can submit activity.
Use a stremio:// installation link with an HTTPS-copy fallback.

Show Awaiting first event, Receiving events, Paused, Needs attention or
Disconnected. Last received and last applied are separate timestamps; silence
alone does not prove an unsupported client or disconnected installation.
Activity lists title/episode, receipt time, outcome and sanitized reason.
Unknown identities offer an explicit match to an existing owned entity with a
verified external reference. A confirmation cannot bypass episode validation.

Details show Stremio source, last sampled position/duration and update time.
A partially played movie remains TO_BE_SEEN until watched; no new movie status.
Keep existing TV controls and manual completion history. Explain before install
that activity from a shared Stremio account goes to this Know owner.

Pause stops ingestion/application while preserving progress. Rotate invalidates
the old URL immediately and requires reinstalling the replacement. Disconnect
revokes access and cancels pending work; applied personal history remains.
Clear activity is separate from deleting progress. Manual corrections remain
available throughout.

## Identity

Match verified IMDb movie IDs against Phase 11 movie external references and
verified IMDb series IDs against TVMaze external references. Parse Cinemeta
episodes as ttID:season:episode and resolve the exact episode of the mapped show.
Never title-match, equate TVMaze IDs with IMDb IDs or cross movie/series types.
Specials and alternate ordering require a verified mapping. Unknown, ambiguous or
unsupported identities go to Needs attention without changing progress. Multiple
owned entities with the same identity require explicit target selection.
No automatic creation or merging. Reuse provider caches and budgets for background
resolution; never call a metadata provider on the ingestion/startup path.

## Progress rules

| Received event | Know behavior |
| --- | --- |
| player start / pause | Save a valid position/duration sample; no completion. A backwards seek can lower position. |
| player stop below 90%, or unknown duration | Save valid partial progress; no completion. |
| player stop at least 90% of positive duration | Mark this movie/episode watched once. Seeking to the end can satisfy this product threshold. |
| library watched movie | Mark watched and add a source-linked completion only on the imported unwatched-to-watched transition. |
| library watched episode list | Validate every video ID belongs to the parent series; apply exact episodes, up to 100 per event. Invalid batch fails atomically. |
| library watched/unwatched whole series without videoId | Needs attention; require a reviewed episode selection instead of expanding a whole series. |
| library unwatched | Pending correction requiring confirmation; never automatically erase manual history or watched flags. |
| libraryAdd / libraryRemove | Record as ignored; no entity creation, deletion or status change. |
| catalog / meta / stream request | Never playback evidence. These resources are not advertised. |

Reuse TV release eligibility, watched flags and completion invariants through
application services. Marking an episode never marks its predecessors. Keep
marking the entire show SEEN as the existing explicit caught-up action. Movie
watched state, status and completion insertion commit atomically. Duplicate stop
and watched events do not create rewatch completions: Phase 12 imports watched
state, while explicit Know actions continue to record repeat viewings.

## Protocol and API

Implement an HTTP adapter in the existing Spring backend; a Node sidecar is not
required by the protocol. Call provider-neutral progress application commands.
Proposed management routes use existing authentication, ownership and error JSON.
Wrong owner and missing resources both return 404; stale versions return 409.

| Method | Route | Contract |
| --- | --- | --- |
| GET | /api/v1/integrations/stremio | Status, version, timestamps and counts; no existing secret |
| POST | /api/v1/integrations/stremio | Create one connection per owner; return install URLs once; 409 if already connected |
| PATCH | /api/v1/integrations/stremio | Expected version and paused boolean |
| POST | /api/v1/integrations/stremio/rotate | Expected version; return replacement URLs once |
| DELETE | /api/v1/integrations/stremio | Expected version; revoke and cancel pending work |
| GET | /api/v1/integrations/stremio/activity | Owner-only cursor pagination and sanitized outcomes |
| POST | /api/v1/integrations/stremio/activity/{id}/resolve | Expected activity/entity/mapping versions, explicit target and confirmation; revalidate current ownership/identity |
| DELETE | /api/v1/integrations/stremio/activity | Clear activity, retaining applied history and minimal deduplication state |

Private addon base: /integrations/stremio/{secret}. It exposes:

- GET /manifest.json
- GET /player/{type}/{videoId}/{extra}.json
- GET /library/{type}/{metaId}/{extra}.json

```json
{
  "id": "app.know.progress",
  "version": "1.0.0",
  "name": "Know progress",
  "description": "Sync movie and episode progress to Know",
  "types": ["movie", "series"],
  "resources": [
    {"name": "player", "types": ["movie", "series"], "idPrefixes": ["tt"]},
    {"name": "library", "types": ["movie", "series"], "idPrefixes": ["tt"]}
  ],
  "catalogs": []
}
```

The deployed manifest ID must use a project-controlled namespace. A configure
landing page directs users to authenticated settings; never accept arbitrary
owner IDs in addon configuration.

Extras are a URL-encoded query-string path segment, not a JSON POST body.
Player actions are start/pause/stop, with currentTime and duration strings in
milliseconds. Library actions are watched/unwatched/libraryAdd/libraryRemove,
with an optional comma-separated videoId list. Decode once, reject duplicate
keys, validate strict ID grammar and bound encoded/decoded sizes. Times must be
finite nonnegative bounded integers; duration zero means unknown, and position
above a known duration is invalid. Initial decoded extras limit: 16 KiB. Configure
the edge URI limit accordingly and verify 100-episode batches fit before release.

Accepted durable events return 200 {"success":true}; malformed events return
sanitized 400; invalid/revoked secrets return indistinguishable 404; failed durable
writes or overload return 503. Stremio does not use the event response, so do not
rely on retries or user-visible acknowledgement. Unknown types/actions never
mutate progress. Pin exact parser/response fixtures during the compatibility spike.

## Security and operations

Use public HTTPS and wildcard CORS on addon routes as required by the protocol,
without cookies or credentialed CORS. Existing API authentication/CORS stays
scoped separately. A random 256-bit opaque path capability identifies one owner
and permits only event ingestion. Store only its hash. Never embed emails, JWTs
or provider keys. A leaked URL permits forged activity until revoked; CORS does
not authenticate Stremio and this is not trusted proof of viewing.

Protocol GETs cause side effects. Use Cache-Control: no-store on manifests, events
and errors; bypass CDN/proxy caching and prefetch. Redact secret-bearing paths and
query strings from edge/app/APM logs and exclude them from analytics. Use
Referrer-Policy: no-referrer and no third-party assets on install pages. Verify
these protections across infrastructure before enabling real accounts.

Bound per-connection/global traffic and queue depth. Initial configurable budget:
120 events/minute/connection, burst 30, 10,000 pending events/connection. Never
fetch URLs supplied in event data. Worker application rechecks pause/revocation.
Account/entity deletion removes scoped sync data and prevents queued events from
recreating deleted entities. No startup provider calls; preserve the existing
under-five-second save-ready target.

## Storage, ordering and conflict policy

Use the next unused migration number; proposed tables:

- stremio_connections: unique owner, unique token hash, state, version, timestamps.
- stremio_event_inbox: connection, receipt sequence, bounded normalized payload,
  hash, receivedAt, outcome, attempts, nextAttemptAt, mapping/progress revision.
- external_playback_progress: owner/entity/optional episode, positionMs,
  durationMs, receivedAt, source and revision; unique target/source per owner.
- external_watched_state: owner/target/source, imported watched state, linked
  completion ID and applied sequence; retained independently of inbox cleanup.

Persist before acknowledgement. Use restart-safe worker claims, bounded
exponential retries and dead-letter outcomes. Deduplicate canonical payloads
within five minutes; retain durable watched-state uniqueness beyond that window.
Do not permanently deduplicate identical position events: a later seek can repeat
the same sample. Process accepted events in receipt order per connection with
per-target locks for concurrent manual edits. Commit progress, watched state,
completion and application outcome atomically. A delayed metadata resolution
must not indefinitely block unrelated entities; retain target order when skipping
unresolved work.

Receipt order cannot recover actual cross-device chronology without timestamps
and session IDs. Disclose this limitation in settings. Capture target manual-edit
and mapping revisions when resolving a received event; if they change before
application, record CONFLICT instead of overwriting. Unresolved historical events
require confirmation against current versions before replay. Rematch/deletion
invalidates pending target bindings. Later received events are new observations;
Pause is the explicit way to preserve a manual override from further sync.
Imported sequence revisions must be distinct from manual revisions so sequential
events do not conflict with each other. Never call HTTP controllers internally to
bypass expected-version checks; share invariant-preserving application commands.

Retention defaults: normalized event payloads seven days, sanitized outcomes and
unresolved items 30 days. Disconnect deletes transient sync data while retaining
source-linked personal progress/completion provenance and minimum watched-state
deduplication. Clear activity has the same preservation rule. Entity/account
deletion removes it. Track aggregate outcomes, queue lag and failures without
titles, secrets or account identifiers in metric labels.

## Delivery and acceptance

User target platforms: LG webOS, Android phones and Android tablets (confirmed
8 November 2026).
Prioritize compatibility checks on these devices. Exact OS and Stremio app
versions must be recorded during live verification. Test Android phones and
tablets separately; Android TV is not a user target for this phase.
Treat webOS event support as unverified until captured requests demonstrate it.
If either target client does not emit the required events, report the limitation
and retain manual progress rather than claiming automatic sync works.

1. Compatibility spike: pin official protocol/SDK revision and capture real
   requests on web, desktop, Android mobile, Android TV and iOS where available.
   Record client version, install flow, event opt-in, seek/stop, watched/unwatched,
   batch handling and event delivery with streams from another addon. Publish
   only verified clients as supported; others remain unverified.
2. Backend: capability lifecycle, strict parser, durable inbox, verified identity
   resolution and transactional progress commands behind a feature flag off by
   default. Export management OpenAPI and separately document protocol GET routes.
3. Webapp: settings lifecycle, activity/conflict resolution and source/position
   details. Verify keyboard use, narrow screens, light/dark themes and secret masking.
4. Validation: fixture-based unit tests plus HTTP-to-PostgreSQL integration tests
   using Testcontainers and stub metadata providers. No live provider calls in
   routine automated tests. Test install/event/apply/pause/rotate/disconnect;
   89%/90% boundary; unknown duration; seek and invalid/overflow times; duplicate
   completions; exact episodes/specials; ambiguous IDs; atomic 100-video batches;
   late/multi-device events; manual-edit/rematch/deletion races; worker restart;
   revoke-during-application; cross-account isolation; cache/log redaction;
   failed durable writes; existing TV/movie behavior and startup readiness.

Release gates are measured client compatibility and infrastructure secret
redaction. Do not promise historical backfill or guaranteed resume updates.

## Official references

Checked 8 November 2026. Living documents must be pinned during implementation:

- [Protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md)
- [Player events](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/definePlayerHandler.md)
- [Library events](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineLibraryHandler.md)
- [Manifest](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md)
- [Install deep links](https://stremio.github.io/stremio-addon-sdk/deep-links.html)
