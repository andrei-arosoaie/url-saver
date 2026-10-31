ALTER TABLE articles ADD COLUMN category_source TEXT NOT NULL DEFAULT 'DEFAULT'
    CHECK (category_source IN ('DEFAULT','USER','AI'));
ALTER TABLE articles ADD COLUMN name_source TEXT NOT NULL DEFAULT 'DEFAULT'
    CHECK (name_source IN ('DEFAULT','USER','AI'));
-- Existing metadata was edited manually before AI identification existed.
-- Even OTHER might have been selected deliberately; preserve every existing category.
UPDATE articles SET category_source='USER';
UPDATE articles SET name_source='USER' WHERE identified_entity_name IS NOT NULL;

CREATE TABLE identification_jobs (
    article_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','SKIPPED')),
    reason TEXT,
    provider TEXT,
    model TEXT,
    subjects JSONB NOT NULL DEFAULT '[]',
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    claim_id UUID,
    FOREIGN KEY(account_id,article_id) REFERENCES articles(account_id,id) ON DELETE CASCADE
);
CREATE INDEX identification_jobs_pending_idx ON identification_jobs(requested_at)
    WHERE status IN ('PENDING','RUNNING');

CREATE TABLE identification_provider_health (
    provider TEXT PRIMARY KEY,
    consecutive_failures INTEGER NOT NULL DEFAULT 0 CHECK(consecutive_failures >= 0),
    cooldown_until TIMESTAMPTZ,
    last_reason TEXT,
    claim_id UUID,
    lease_until TIMESTAMPTZ
);
