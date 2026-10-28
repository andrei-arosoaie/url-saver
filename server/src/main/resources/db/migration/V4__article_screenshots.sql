CREATE TABLE article_screenshots (
    article_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    capture_id UUID,
    source_hash TEXT,
    lease_id UUID,
    lease_until TIMESTAMPTZ,
    provider TEXT,
    file_id TEXT,
    file_path TEXT,
    content_type TEXT,
    byte_size BIGINT,
    width INTEGER,
    height INTEGER,
    FOREIGN KEY (account_id, article_id) REFERENCES articles(account_id, id),
    CHECK ((file_id IS NULL AND file_path IS NULL) OR
           (provider IS NOT NULL AND file_id IS NOT NULL AND file_path IS NOT NULL
            AND byte_size > 0 AND width > 0 AND height > 0))
);

CREATE TABLE screenshot_cleanup (
    id UUID PRIMARY KEY,
    provider TEXT NOT NULL,
    file_id TEXT,
    file_path TEXT NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    retain_until TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX screenshot_cleanup_due_idx ON screenshot_cleanup(next_attempt_at);
