CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE articles (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts (id),
    url TEXT NOT NULL,
    url_hash BYTEA NOT NULL,
    title TEXT NOT NULL,
    content TEXT,
    saved_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT articles_version_nonnegative CHECK (version >= 0),
    CONSTRAINT articles_url_hash_matches CHECK (
        url_hash = sha256(convert_to(url, 'UTF8'))
    ),
    CONSTRAINT articles_account_url_hash_unique UNIQUE (account_id, url_hash)
);

CREATE INDEX articles_account_saved_cursor_idx
    ON articles (account_id, saved_at DESC, id DESC);
