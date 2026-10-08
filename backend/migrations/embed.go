// Package migrations embeds the goose SQL migrations so the binary can apply
// them on start.
package migrations

import "embed"

// FS holds every migration file.
//
//go:embed *.sql
var FS embed.FS
