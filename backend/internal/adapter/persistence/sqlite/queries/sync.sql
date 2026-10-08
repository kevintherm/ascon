-- name: SyncField :one
SELECT value, updated_at FROM sync_fields
WHERE account_id = ? AND entity = ? AND record_id = ? AND field = ?;

-- name: PutSyncField :exec
INSERT INTO sync_fields (account_id, entity, record_id, field, value, updated_at, seq)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (account_id, entity, record_id, field) DO UPDATE SET
  value = excluded.value, updated_at = excluded.updated_at, seq = excluded.seq;

-- name: SyncTombstone :one
SELECT updated_at FROM sync_tombstones
WHERE account_id = ? AND entity = ? AND record_id = ?;

-- name: PutSyncTombstone :exec
INSERT INTO sync_tombstones (account_id, entity, record_id, updated_at, seq)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT (account_id, entity, record_id) DO UPDATE SET
  updated_at = excluded.updated_at, seq = excluded.seq;

-- name: NextSyncSeq :one
INSERT INTO sync_clocks (account_id, seq) VALUES (?, 1)
ON CONFLICT (account_id) DO UPDATE SET seq = seq + 1
RETURNING seq;

-- name: SyncSince :many
SELECT entity, record_id, field, value, updated_at, seq FROM sync_fields
WHERE sync_fields.account_id = sqlc.arg(account_id) AND sync_fields.seq > sqlc.arg(cursor)
UNION ALL
SELECT entity, record_id, '' AS field, '' AS value, updated_at, seq FROM sync_tombstones
WHERE sync_tombstones.account_id = sqlc.arg(account_id) AND sync_tombstones.seq > sqlc.arg(cursor)
ORDER BY seq
LIMIT sqlc.arg(row_limit);

-- name: SyncAtSeq :many
SELECT entity, record_id, field, value, updated_at, seq FROM sync_fields
WHERE sync_fields.account_id = sqlc.arg(account_id) AND sync_fields.seq = sqlc.arg(seq)
UNION ALL
SELECT entity, record_id, '' AS field, '' AS value, updated_at, seq FROM sync_tombstones
WHERE sync_tombstones.account_id = sqlc.arg(account_id) AND sync_tombstones.seq = sqlc.arg(seq);
