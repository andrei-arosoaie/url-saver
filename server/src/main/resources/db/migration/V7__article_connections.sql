-- Keep application tables in public; support Supabase's existing extensions schema too.
SET LOCAL search_path TO public, extensions, pg_catalog;
CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE articles ADD COLUMN connection_revision BIGINT NOT NULL DEFAULT 0 CHECK(connection_revision>=0);

CREATE TABLE embedding_profiles (
    id UUID PRIMARY KEY,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    dimensions INTEGER NOT NULL CHECK(dimensions BETWEEN 128 AND 3072),
    chunking_config JSONB NOT NULL,
    config_hash BYTEA NOT NULL UNIQUE CHECK(octet_length(config_hash)=32),
    created_at TIMESTAMPTZ NOT NULL,
    backfill_after UUID,
    backfill_complete BOOLEAN NOT NULL DEFAULT FALSE,
    matching_config_hash BYTEA NOT NULL CHECK(octet_length(matching_config_hash)=32)
);

CREATE TABLE article_embeddings (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    article_id UUID NOT NULL,
    profile_id UUID NOT NULL REFERENCES embedding_profiles(id),
    content_hash BYTEA NOT NULL CHECK(octet_length(content_hash)=32),
    source_article_version BIGINT NOT NULL CHECK(source_article_version>=0),
    chunk_index INTEGER NOT NULL CHECK(chunk_index>=0),
    text_start INTEGER NOT NULL CHECK(text_start>=0),
    text_end INTEGER NOT NULL CHECK(text_end>text_start),
    token_count INTEGER NOT NULL CHECK(token_count BETWEEN 1 AND 2040),
    embedding vector NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(account_id,article_id) REFERENCES articles(account_id,id),
    UNIQUE(account_id,article_id,profile_id,content_hash,chunk_index),
    CHECK ((embedding <#> embedding)<0)
);
CREATE INDEX article_embeddings_owner_profile_idx ON article_embeddings(account_id,profile_id,article_id,content_hash);
-- Profile-specific validation permits future model/dimension changes without mixing vectors.
CREATE FUNCTION check_embedding_dimensions() RETURNS trigger LANGUAGE plpgsql
SET search_path=public,extensions,pg_catalog AS $$
BEGIN
    IF vector_dims(NEW.embedding) <> (SELECT dimensions FROM embedding_profiles WHERE id=NEW.profile_id) THEN
        RAISE EXCEPTION 'Embedding dimensions do not match profile' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER article_embedding_dimensions BEFORE INSERT OR UPDATE ON article_embeddings
FOR EACH ROW EXECUTE FUNCTION check_embedding_dimensions();

CREATE TABLE article_connections (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    article_low_id UUID NOT NULL,
    article_high_id UUID NOT NULL,
    semantic_profile_id UUID REFERENCES embedding_profiles(id),
    semantic_score DOUBLE PRECISION CHECK(semantic_score BETWEEN -1 AND 1),
    low_content_hash BYTEA,
    high_content_hash BYTEA,
    low_revision BIGINT NOT NULL CHECK(low_revision>=0),
    high_revision BIGINT NOT NULL CHECK(high_revision>=0),
    evaluated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    FOREIGN KEY(account_id,article_low_id) REFERENCES articles(account_id,id),
    FOREIGN KEY(account_id,article_high_id) REFERENCES articles(account_id,id),
    UNIQUE(account_id,article_low_id,article_high_id),
    UNIQUE(account_id,id),
    CHECK(article_low_id<article_high_id),
    CHECK ((semantic_score IS NULL AND semantic_profile_id IS NULL AND low_content_hash IS NULL AND high_content_hash IS NULL)
        OR (semantic_score IS NOT NULL AND semantic_profile_id IS NOT NULL AND octet_length(low_content_hash)=32 AND octet_length(high_content_hash)=32))
);
CREATE INDEX article_connections_low_idx ON article_connections(account_id,article_low_id);
CREATE INDEX article_connections_high_idx ON article_connections(account_id,article_high_id);

CREATE TABLE connection_shared_entities (
    account_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(connection_id,entity_id),
    FOREIGN KEY(account_id,connection_id) REFERENCES article_connections(account_id,id),
    FOREIGN KEY(account_id,entity_id) REFERENCES entities(account_id,id)
);

CREATE TABLE article_connection_exclusions (
    account_id UUID NOT NULL,
    article_low_id UUID NOT NULL,
    article_high_id UUID NOT NULL,
    removed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(account_id,article_low_id,article_high_id),
    FOREIGN KEY(account_id,article_low_id) REFERENCES articles(account_id,id),
    FOREIGN KEY(account_id,article_high_id) REFERENCES articles(account_id,id),
    CHECK(article_low_id<article_high_id)
);

CREATE TABLE article_connection_jobs (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    article_id UUID NOT NULL,
    kind TEXT NOT NULL CHECK(kind IN ('EMBEDDING','CONNECTIONS')),
    profile_id UUID NOT NULL REFERENCES embedding_profiles(id),
    processed_profile_id UUID REFERENCES embedding_profiles(id),
    requested_revision BIGINT NOT NULL CHECK(requested_revision>=0),
    processed_revision BIGINT NOT NULL DEFAULT 0 CHECK(processed_revision>=0),
    claimed_revision BIGINT,
    rerun_requested BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
    available_at TIMESTAMPTZ NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count>=0),
    content_hash BYTEA,
    chunk_plan JSONB NOT NULL DEFAULT '[]',
    last_error_code TEXT,
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(account_id,article_id) REFERENCES articles(account_id,id),
    UNIQUE(account_id,article_id,kind)
);
CREATE INDEX article_connection_jobs_eligible_idx ON article_connection_jobs(available_at,created_at) WHERE status='PENDING';
CREATE INDEX article_connection_jobs_expired_idx ON article_connection_jobs(lease_until) WHERE status='RUNNING';

CREATE TABLE embedding_provider_cooldowns (
    quota_scope TEXT PRIMARY KEY,
    provider TEXT NOT NULL,
    blocked_until TIMESTAMPTZ,
    reason TEXT,
    last_http_status INTEGER,
    window_started_at TIMESTAMPTZ NOT NULL,
    requests_in_window INTEGER NOT NULL DEFAULT 0 CHECK(requests_in_window>=0),
    budget_day DATE NOT NULL,
    embeddings_today INTEGER NOT NULL DEFAULT 0 CHECK(embeddings_today>=0),
    updated_at TIMESTAMPTZ NOT NULL
);
-- Additional operational table: permits enforce a global limit across all app instances.
CREATE TABLE embedding_provider_permits (
    lease_token UUID PRIMARY KEY,
    quota_scope TEXT NOT NULL REFERENCES embedding_provider_cooldowns(quota_scope),
    job_id UUID NOT NULL REFERENCES article_connection_jobs(id),
    lease_until TIMESTAMPTZ NOT NULL
);
CREATE INDEX embedding_provider_permits_scope_idx ON embedding_provider_permits(quota_scope,lease_until);
