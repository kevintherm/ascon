-- name: InsertCandidate :exec
INSERT INTO rule_candidates (id, domain, account_id, status, reason, rule_version, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

-- name: GetCandidate :one
SELECT id, domain, account_id, status, reason, rule_version, created_at, updated_at
FROM rule_candidates
WHERE id = ? AND account_id = ?;

-- name: HasPendingCandidate :one
SELECT EXISTS (
  SELECT 1 FROM rule_candidates WHERE domain = ? AND status = 'pending'
);

-- name: ResolveCandidates :exec
UPDATE rule_candidates
SET status = ?, reason = ?, rule_version = ?, updated_at = ?
WHERE domain = ? AND status = 'pending';

-- name: AbandonPendingCandidates :execrows
UPDATE rule_candidates
SET status = 'rejected', reason = ?, updated_at = ?
WHERE status = 'pending';

-- name: RecordGenerationFailure :exec
INSERT INTO generation_failures (domain, reason, failed_at)
VALUES (?, ?, ?)
ON CONFLICT (domain) DO UPDATE SET reason = excluded.reason, failed_at = excluded.failed_at;

-- name: LastGenerationFailure :one
SELECT failed_at FROM generation_failures WHERE domain = ?;
