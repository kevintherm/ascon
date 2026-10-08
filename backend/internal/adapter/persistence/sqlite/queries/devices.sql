-- name: CreateDevice :exec
INSERT INTO devices (id, token_hash, app_version, webview_version, created_at)
VALUES (?, ?, ?, ?, ?);

-- name: DeviceByTokenHash :one
SELECT id, app_version, webview_version, created_at
FROM devices
WHERE token_hash = ?;

-- name: DeleteDevice :exec
DELETE FROM devices WHERE id = ?;
