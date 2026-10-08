-- name: LatestRule :one
SELECT body, status, created_at
FROM rules
WHERE domain = ? AND status != 'retired'
ORDER BY version DESC
LIMIT 1;

-- name: GetRule :one
SELECT body, status, created_at FROM rules WHERE domain = ? AND version = ?;

-- name: FingerprintedRules :many
SELECT r.body, r.status, r.created_at
FROM rules r
WHERE r.fingerprint IS NOT NULL
  AND r.status != 'retired'
  AND r.version = (
    SELECT MAX(r2.version) FROM rules r2
    WHERE r2.domain = r.domain AND r2.status != 'retired'
  );

-- name: InsertRule :exec
INSERT INTO rules (domain, version, body, fingerprint, status, created_at)
VALUES (?, ?, ?, ?, ?, ?);

-- name: SetRuleStatus :exec
UPDATE rules SET status = ? WHERE domain = ? AND version = ?;

-- name: RuleHealth :one
SELECT successes, empty_results, backward_jumps
FROM rule_health
WHERE domain = ? AND version = ?;

-- name: AddRuleHealth :one
INSERT INTO rule_health (domain, version, successes, empty_results, backward_jumps)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT (domain, version) DO UPDATE SET
  successes = successes + excluded.successes,
  empty_results = empty_results + excluded.empty_results,
  backward_jumps = backward_jumps + excluded.backward_jumps
RETURNING successes, empty_results, backward_jumps;

-- name: AddRuleReport :exec
INSERT INTO rule_reports (domain, version, device_id, problem, url, correction, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT DO NOTHING;

-- name: ReportingDevices :one
SELECT COUNT(DISTINCT device_id) FROM rule_reports WHERE domain = ? AND version = ?;

-- name: MaxRuleVersion :one
SELECT CAST(COALESCE(MAX(version), 0) AS INTEGER) FROM rules WHERE domain = ?;
