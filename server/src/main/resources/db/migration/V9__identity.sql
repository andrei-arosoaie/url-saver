CREATE TABLE external_account_providers (
    issuer TEXT NOT NULL,
    subject TEXT NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id),
    PRIMARY KEY (issuer, subject),
    UNIQUE (account_id, issuer)
);

CREATE TABLE identity_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    client_id TEXT NOT NULL,
    signed_in_at TIMESTAMPTZ NOT NULL,
    refresh_expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE identity_codes (
    code_hash TEXT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    client_id TEXT NOT NULL,
    redirect_uri TEXT NOT NULL,
    challenge TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE identity_refresh_tokens (
    token_hash TEXT PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES identity_sessions(id),
    used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX identity_refresh_session_idx ON identity_refresh_tokens(session_id);
