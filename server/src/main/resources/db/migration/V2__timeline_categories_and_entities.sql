-- Phase 2: account categories, article metadata, entities and completion history.
-- Existing accounts/articles tables remain owned by Phase 1.
-- Category keys are stable identifiers; names/colors are data, not enums.
CREATE TABLE categories (
    account_id UUID NOT NULL REFERENCES accounts(id),
    key TEXT NOT NULL,
    display_name TEXT NOT NULL,
    color TEXT NOT NULL,
    is_default BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    PRIMARY KEY(account_id,key)
);
-- Seed the same seven defaults for existing accounts; account creation seeds future accounts.
INSERT INTO categories(account_id,key,display_name,color,is_default,enabled,created_at)
SELECT a.id,c.key,c.name,c.color,TRUE,TRUE,CURRENT_TIMESTAMP
FROM accounts a CROSS JOIN (VALUES
    ('ARCHITECTURE','Architecture','#305ea7'),
    ('DEVELOPMENT','Development','#8654b0'),
    ('OTHER','Other','#be923b'),
    ('BOOKS','Books','#168e91'),
    ('MOVIES','Movies','#d66f57'),
    ('TV_SHOWS','TV shows','#c65e96'),
    ('GAMES','Games','#4a9a73')
) AS c(key,name,color);
ALTER TABLE articles ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER';
ALTER TABLE articles ADD COLUMN identified_entity_name TEXT;
ALTER TABLE articles ADD CONSTRAINT articles_category_owner_fk
    FOREIGN KEY(account_id,category) REFERENCES categories(account_id,key);
ALTER TABLE articles ADD CONSTRAINT articles_account_id_unique UNIQUE (account_id,id);
CREATE INDEX articles_account_category_cursor_idx
    ON articles(account_id,category,saved_at DESC,id DESC);

CREATE TABLE entities (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    -- Java EntityType enum persisted by name (STRING), never ordinal.
    type TEXT NOT NULL CHECK (type IN ('BOOK','MOVIE','TV_SHOW','GAME')),
    title TEXT NOT NULL CHECK (length(title)>0),
    title_hash BYTEA NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
    CONSTRAINT entities_title_hash_matches CHECK (
        title_hash=sha256(convert_to(title,'UTF8'))
    ),
    CONSTRAINT entities_owner_type_title_unique UNIQUE(account_id,type,title_hash),
    CONSTRAINT entities_account_id_unique UNIQUE(account_id,id),
    CONSTRAINT entities_status_matches_type CHECK (
        (type='BOOK' AND status IN ('TO_READ','READING','READ')) OR
        (type IN ('MOVIE','TV_SHOW') AND status IN ('TO_BE_SEEN','SEEN')) OR
        (type='GAME' AND status IN ('TO_PLAY','PLAYING','FINISHED'))
    )
);
CREATE INDEX entities_account_type_cursor_idx ON entities(account_id,type,created_at DESC,id DESC);
CREATE INDEX entities_account_type_status_cursor_idx ON entities(account_id,type,status,created_at DESC,id DESC);

CREATE TABLE article_entities (
    account_id UUID NOT NULL,
    article_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(article_id,entity_id),
    FOREIGN KEY(account_id,article_id) REFERENCES articles(account_id,id),
    FOREIGN KEY(account_id,entity_id) REFERENCES entities(account_id,id)
);
CREATE INDEX article_entities_entity_idx ON article_entities(account_id,entity_id,article_id);

CREATE TABLE entity_completions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    FOREIGN KEY(account_id,entity_id) REFERENCES entities(account_id,id)
);
CREATE INDEX entity_completions_entity_cursor_idx
    ON entity_completions(account_id,entity_id,completed_at DESC,id DESC);
CREATE INDEX entity_completions_owner_year_idx
    ON entity_completions(account_id,completed_at,entity_id);

-- Exact unmodified title is hashed in application code and verified by PostgreSQL.
-- On matching digest, compare the full title before reuse or duplicate rejection.
-- A true digest collision returns ENTITY_TITLE_HASH_COLLISION, never merges titles.
-- No cascade deletes: unlink deletes one article_entities row only.
-- Completion corrections do not rewrite entity/article creation timestamps.
-- UTC year query uses [year-01-01T00:00:00Z,(year+1)-01-01T00:00:00Z).
-- Apply case-insensitive title search separately from exact duplicate identity.
