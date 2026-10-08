-- name: CreateAccount :exec
INSERT INTO accounts (id, tier, created_at) VALUES (?, ?, ?);

-- name: CreateAccountToken :exec
INSERT INTO account_tokens (token_hash, account_id, created_at) VALUES (?, ?, ?);

-- name: AccountByTokenHash :one
SELECT a.id, a.tier
FROM account_tokens t
JOIN accounts a ON a.id = t.account_id
WHERE t.token_hash = ?;

-- name: EnsureQuotaRow :exec
INSERT INTO quota_usage (account_id, period, used) VALUES (?, ?, 0)
ON CONFLICT (account_id, period) DO NOTHING;

-- name: UseQuota :one
UPDATE quota_usage SET used = used + 1
WHERE account_id = sqlc.arg(account_id) AND period = sqlc.arg(period) AND used < sqlc.arg(quota_limit)
RETURNING used;

-- name: RefundQuota :exec
UPDATE quota_usage SET used = used - 1
WHERE account_id = ? AND period = ? AND used > 0;

-- name: QuotaUsed :one
SELECT used FROM quota_usage WHERE account_id = ? AND period = ?;
