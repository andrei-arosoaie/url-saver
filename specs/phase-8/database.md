# Phase 8 database design — review draft

No Flyway migration or production implementation is included.

## tvmaze_shows

Shared public metadata

tvmaze_id BIGINT PK; name TEXT; poster_url TEXT NULL; summary_html TEXT NULL; genres JSONB; provider_status TEXT NULL; average_runtime INTEGER NULL; rating NUMERIC NULL; imdb_id TEXT NULL; next_episode_id BIGINT NULL; provider_updated_at TIMESTAMPTZ NULL; fetched_at TIMESTAMPTZ; version BIGINT

Store only selected display fields plus provenance. TVMaze rating is not IMDb rating. Sanitize summary HTML.

## tvmaze_seasons

Shared season catalog

tvmaze_id BIGINT PK; show_id BIGINT FK; number INTEGER; name TEXT NULL; premiere_date DATE NULL; end_date DATE NULL; fetched_at TIMESTAMPTZ; version BIGINT

Show/season IDs identify provider records. Do not use the season number as a global key.

## tvmaze_episodes

Shared episode catalog

tvmaze_id BIGINT PK; show_id BIGINT FK; season_id BIGINT FK NULL; season_number INTEGER; number INTEGER NULL; episode_type TEXT; name TEXT; air_at TIMESTAMPTZ NULL; air_date DATE NULL; runtime INTEGER NULL; image_url TEXT NULL; summary_html TEXT NULL; fetched_at TIMESTAMPTZ; provider_present BOOLEAN; version BIGINT

Retain unavailable/deleted provider IDs referenced by progress; do not cascade-delete personal state. Select/watch regular aired episodes only. Missing time handling is highlighted for review.

## entity_tv_shows

Private mapping, position and show-seen watermark

account_id UUID; entity_id UUID; show_id BIGINT FK; alias TEXT NULL; last_watched_episode_id BIGINT NULL; last_marked_seen_at TIMESTAMPTZ NULL; new_episodes_available BOOLEAN DEFAULT FALSE; created_at TIMESTAMPTZ; updated_at TIMESTAMPTZ; version BIGINT

PK(account_id, entity_id), owner-checked FK to entities. Validate entity type TV_SHOW. Position and checkmarks are independent; manual entity title is never replaced by provider refresh.

## entity_episode_watches

Private episode checkmarks

account_id UUID; entity_id UUID; episode_id BIGINT FK; watched_at TIMESTAMPTZ; version BIGINT

PK(account_id, entity_id, episode_id). Composite FK to entity_tv_shows. A row means watched; explicit uncheck removes it. Validate episode belongs to the mapped show. Not an episode rewatch-history feature.

## tvmaze_refresh_jobs

Durable, shared catalog refresh

show_id BIGINT PK/FK; requested_at TIMESTAMPTZ; available_at TIMESTAMPTZ; status TEXT; lease_token UUID NULL; lease_until TIMESTAMPTZ NULL; attempt_count INTEGER; last_error_code TEXT NULL; updated_at TIMESTAMPTZ

One coalesced refresh per show. Short claim transaction with SKIP LOCKED, expiring leases and fencing. Reuse a public catalog across accounts; never send personal progress to TVMaze.

## tvmaze_provider_state

Independent provider backoff

quota_scope TEXT PK; blocked_until TIMESTAMPTZ NULL; last_http_status INTEGER NULL; updated_at TIMESTAMPTZ

Coordinate TVMaze requests across pods sharing outbound IP. Short provider-specific backoff/Retry-After; independent of the Gemini 24-hour policy. Exact values are technical defaults to validate.


## tvmaze_search_cache

Shared PostgreSQL search-result cache, surviving restarts and reused across pods.

query_key TEXT PK; normalized_query TEXT; result_payload JSONB; fetched_at TIMESTAMPTZ; expires_at TIMESTAMPTZ; refresh_lease_token UUID NULL; refresh_lease_until TIMESTAMPTZ NULL

Key includes provider and all result-affecting query parameters. Coalesce refreshes through expiring leases. Store complete result list; first eight is a UI page size, not a total result limit. Failures are not empty results. Proposed configurable TTLs: positive 24h, empty 15m, bounded stale-on-error 7d. These cache TTLs remain technical proposals.

## Existing entities status extension

Phase 8 adds backend enum value SEEING for TV_SHOW, with allowed statuses TO_BE_SEEN / SEEING / SEEN. Update the database status/type CHECK in the Phase 8 migration; movie statuses stay unchanged. Selecting SEEING does not append completion history or clear episode progress.

Recording a last-watched position or marking an episode watched atomically changes TO_BE_SEEN to SEEING and increments affected versions. Position-only still counts as viewing progress; unchecking alone does not. Never append completion history for SEEING.

## Identified title and alias

Matched TV-show canonical title is read-only to users. Owner-specific alias is nullable TEXT on entity_tv_shows, displayed after the title and independently editable. Unmatched titles remain editable. Alias never participates in provider identity or completion/progress mutation.

## Approved identity / correction rules

- Change show requires explicit confirmation before atomically resetting old show-specific position/checkmarks. Preserve article links and completion history.
- Unknown air dates permit manual watched marking; known future episodes remain blocked. Unknown-date episodes are not automatically considered aired.
- Different TVMaze IDs may share an entity title. Enforce UNIQUE(account_id, show_id) on mappings. The Phase 8 migration must adapt the existing owner/type/title-hash uniqueness for linked shows while preserving unmatched manual-title rules.

Confirmed Change show also sets current entity status to TO_BE_SEEN and retains the alias, in the same transaction as remapping/progress reset. Preserve completion history and article links; cancel is a no-op.
