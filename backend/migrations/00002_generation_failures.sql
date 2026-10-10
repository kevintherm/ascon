-- +goose Up

-- The latest time AI detection could not write a usable rule for a domain.
-- New requests for the domain are turned away for a while, so a site the
-- model can't read doesn't spend tokens on every visit. Failures of the
-- provider itself are not recorded here.
CREATE TABLE generation_failures (
    domain    TEXT PRIMARY KEY,
    reason    TEXT NOT NULL,
    failed_at TEXT NOT NULL
) STRICT;

-- +goose Down

DROP TABLE generation_failures;
