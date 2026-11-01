CREATE TABLE identity_profiles (
    account_id UUID PRIMARY KEY REFERENCES accounts(id),
    display_name TEXT NOT NULL,
    picture_url TEXT,
    picture_removed BOOLEAN NOT NULL DEFAULT FALSE
);
