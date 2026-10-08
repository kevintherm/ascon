-- +goose Up

-- Anonymous devices. The token itself is never stored, only its SHA-256.
CREATE TABLE devices (
    id              TEXT PRIMARY KEY,
    token_hash      BLOB NOT NULL UNIQUE,
    app_version     TEXT NOT NULL,
    webview_version TEXT,
    created_at      TEXT NOT NULL
) STRICT;

CREATE TABLE accounts (
    id         TEXT PRIMARY KEY,
    tier       TEXT NOT NULL CHECK (tier IN ('free', 'premium')),
    created_at TEXT NOT NULL
) STRICT;

-- Account tokens for the development verifier. A real sign-in flow may
-- replace this table.
CREATE TABLE account_tokens (
    token_hash BLOB PRIMARY KEY,
    account_id TEXT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at TEXT NOT NULL
) STRICT;

-- Every version of every rule is kept, so a bad regeneration can roll back.
CREATE TABLE rules (
    domain      TEXT    NOT NULL,
    version     INTEGER NOT NULL,
    body        BLOB    NOT NULL,
    fingerprint TEXT,
    status      TEXT    NOT NULL CHECK (status IN ('active', 'suspect', 'retired')),
    created_at  TEXT    NOT NULL,
    PRIMARY KEY (domain, version)
) STRICT;

CREATE INDEX rules_fingerprint ON rules (fingerprint) WHERE fingerprint IS NOT NULL AND status != 'retired';

CREATE TABLE rule_health (
    domain         TEXT    NOT NULL,
    version        INTEGER NOT NULL,
    successes      INTEGER NOT NULL DEFAULT 0,
    empty_results  INTEGER NOT NULL DEFAULT 0,
    backward_jumps INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (domain, version),
    FOREIGN KEY (domain, version) REFERENCES rules (domain, version) ON DELETE CASCADE
) STRICT;

-- One report per device, rule version and problem, so one device cannot
-- outvote others by repeating itself.
CREATE TABLE rule_reports (
    domain     TEXT    NOT NULL,
    version    INTEGER NOT NULL,
    device_id  TEXT    NOT NULL,
    problem    TEXT    NOT NULL,
    url        TEXT    NOT NULL,
    correction TEXT,
    created_at TEXT    NOT NULL,
    PRIMARY KEY (domain, version, device_id, problem),
    FOREIGN KEY (domain, version) REFERENCES rules (domain, version) ON DELETE CASCADE
) STRICT;

CREATE TABLE rule_candidates (
    id           TEXT PRIMARY KEY,
    domain       TEXT NOT NULL,
    account_id   TEXT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    status       TEXT NOT NULL CHECK (status IN ('pending', 'accepted', 'rejected')),
    reason       TEXT,
    rule_version INTEGER,
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL
) STRICT;

CREATE INDEX rule_candidates_domain ON rule_candidates (domain, status);

-- AI detection use per account per calendar month in UTC, as 'YYYY-MM'.
CREATE TABLE quota_usage (
    account_id TEXT    NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    period     TEXT    NOT NULL,
    used       INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, period)
) STRICT;

-- Sync stores one row per record field so conflicts resolve per field.
-- seq orders changes within an account for cursor-based pulls.
CREATE TABLE sync_fields (
    account_id TEXT    NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    entity     TEXT    NOT NULL,
    record_id  TEXT    NOT NULL,
    field      TEXT    NOT NULL,
    value      TEXT    NOT NULL,
    updated_at TEXT    NOT NULL,
    seq        INTEGER NOT NULL,
    PRIMARY KEY (account_id, entity, record_id, field)
) STRICT;

CREATE INDEX sync_fields_seq ON sync_fields (account_id, seq);

CREATE TABLE sync_tombstones (
    account_id TEXT    NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    entity     TEXT    NOT NULL,
    record_id  TEXT    NOT NULL,
    updated_at TEXT    NOT NULL,
    seq        INTEGER NOT NULL,
    PRIMARY KEY (account_id, entity, record_id)
) STRICT;

CREATE INDEX sync_tombstones_seq ON sync_tombstones (account_id, seq);

CREATE TABLE sync_clocks (
    account_id TEXT    PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    seq        INTEGER NOT NULL
) STRICT;

-- +goose Down
DROP TABLE sync_clocks;
DROP TABLE sync_tombstones;
DROP TABLE sync_fields;
DROP TABLE quota_usage;
DROP TABLE rule_candidates;
DROP TABLE rule_reports;
DROP TABLE rule_health;
DROP TABLE rules;
DROP TABLE account_tokens;
DROP TABLE accounts;
DROP TABLE devices;
