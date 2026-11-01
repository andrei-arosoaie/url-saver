ALTER TABLE entities DROP CONSTRAINT entities_status_matches_type;
ALTER TABLE entities ADD CONSTRAINT entities_status_matches_type CHECK (
 (type='BOOK' AND status IN ('TO_READ','READING','READ')) OR
 (type='MOVIE' AND status IN ('TO_BE_SEEN','SEEN')) OR
 (type='TV_SHOW' AND status IN ('TO_BE_SEEN','SEEING','SEEN')) OR
 (type='GAME' AND status IN ('TO_PLAY','PLAYING','FINISHED')));
CREATE TABLE tvmaze_shows (
 tvmaze_id BIGINT PRIMARY KEY CHECK(tvmaze_id>0), name TEXT NOT NULL,
 payload JSONB NOT NULL DEFAULT '{}', fetched_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE entities ADD COLUMN tvmaze_id BIGINT REFERENCES tvmaze_shows(tvmaze_id);
ALTER TABLE entities ADD CONSTRAINT entities_tv_type CHECK(tvmaze_id IS NULL OR type='TV_SHOW');
ALTER TABLE entities DROP CONSTRAINT entities_owner_type_title_unique;
CREATE UNIQUE INDEX entities_owner_type_title_unique ON entities(account_id,type,title_hash) WHERE tvmaze_id IS NULL;
CREATE UNIQUE INDEX entities_owner_tvmaze_unique ON entities(account_id,tvmaze_id) WHERE tvmaze_id IS NOT NULL;
CREATE TABLE tvmaze_seasons (
 tvmaze_id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL REFERENCES tvmaze_shows(tvmaze_id),
 number INTEGER NOT NULL, name TEXT, payload JSONB NOT NULL, fetched_at TIMESTAMPTZ NOT NULL,
 UNIQUE(show_id,tvmaze_id)
);
CREATE TABLE tvmaze_episodes (
 tvmaze_id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL REFERENCES tvmaze_shows(tvmaze_id),
 season_id BIGINT, season_number INTEGER NOT NULL, number INTEGER,
 episode_type TEXT NOT NULL, name TEXT NOT NULL, air_at TIMESTAMPTZ, air_date DATE,
 payload JSONB NOT NULL, provider_present BOOLEAN NOT NULL DEFAULT TRUE,
 fetched_at TIMESTAMPTZ NOT NULL, UNIQUE(show_id,tvmaze_id),
 FOREIGN KEY(show_id,season_id) REFERENCES tvmaze_seasons(show_id,tvmaze_id)
);
CREATE INDEX tvmaze_episodes_air_idx ON tvmaze_episodes(show_id,air_at) WHERE provider_present;
CREATE TABLE entity_tv_shows (
 account_id UUID NOT NULL, entity_id UUID NOT NULL, show_id BIGINT NOT NULL REFERENCES tvmaze_shows(tvmaze_id),
 alias TEXT, last_watched_episode_id BIGINT, last_marked_seen_at TIMESTAMPTZ,
 new_episodes_available BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(account_id,entity_id), UNIQUE(account_id,show_id),
 FOREIGN KEY(account_id,entity_id) REFERENCES entities(account_id,id),
 FOREIGN KEY(show_id,last_watched_episode_id) REFERENCES tvmaze_episodes(show_id,tvmaze_id)
);
CREATE TABLE entity_episode_watches (
 account_id UUID NOT NULL, entity_id UUID NOT NULL, episode_id BIGINT NOT NULL REFERENCES tvmaze_episodes(tvmaze_id),
 watched_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(account_id,entity_id,episode_id),
 FOREIGN KEY(account_id,entity_id) REFERENCES entity_tv_shows(account_id,entity_id)
);
CREATE TABLE tvmaze_refresh_jobs (
 show_id BIGINT PRIMARY KEY REFERENCES tvmaze_shows(tvmaze_id), available_at TIMESTAMPTZ NOT NULL,
 lease_token UUID, lease_until TIMESTAMPTZ, attempt_count INTEGER NOT NULL DEFAULT 0,
 last_error_code TEXT
);
CREATE TABLE tvmaze_provider_state (
 quota_scope TEXT PRIMARY KEY, blocked_until TIMESTAMPTZ, lease_token UUID, lease_until TIMESTAMPTZ
);
INSERT INTO tvmaze_provider_state(quota_scope) VALUES ('public');
CREATE TABLE tvmaze_search_cache (
 query_key TEXT PRIMARY KEY, normalized_query TEXT NOT NULL, result_payload JSONB,
 fetched_at TIMESTAMPTZ, expires_at TIMESTAMPTZ,
 refresh_lease_token UUID, refresh_lease_until TIMESTAMPTZ
);
CREATE INDEX tvmaze_cache_expiry_idx ON tvmaze_search_cache(expires_at);
