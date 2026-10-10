-- +goose Up

-- Who signs in to each account: the provider and its stable id for the user,
-- Google's sub. Nothing else about the user is stored. Sign-in tokens go in
-- account_tokens, like the development ones.
CREATE TABLE account_identities (
    provider   TEXT NOT NULL,
    subject    TEXT NOT NULL,
    account_id TEXT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    PRIMARY KEY (provider, subject)
) STRICT;

-- +goose Down

DROP TABLE account_identities;
