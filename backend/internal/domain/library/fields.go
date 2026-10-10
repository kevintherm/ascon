// Package library holds the synced library: series, sources, progress and
// library entries. Sync resolves conflicts per field: the later updatedAt
// wins. contracts/openapi.yaml defines the wire shape.
package library

import (
	"bytes"
	"encoding/json"
	"regexp"
	"time"
)

// Entities that sync.
const (
	Series       = "series"
	Source       = "source"
	Progress     = "progress"
	LibraryEntry = "libraryEntry"
	Chapter      = "chapter"
)

type kind int

const (
	kindNullableString kind = iota
	kindUUID
	kindNullableInt
	kindBool
	kindFraction
	kindDateTime
	kindChapter
	kindStatus
)

// fields lists every field each entity may carry and its kind.
var fields = map[string]map[string]kind{
	Series: {
		"title":          kindNullableString,
		"anilistId":      kindNullableInt,
		"mangaUpdatesId": kindNullableInt,
	},
	Source: {
		"seriesId":       kindUUID,
		"seriesUrl":      kindNullableString,
		"domain":         kindNullableString,
		"lastChapterUrl": kindNullableString,
		"lastChapter":    kindChapter,
	},
	Progress: {
		"seriesId":     kindUUID,
		"chapter":      kindChapter,
		"pagePosition": kindFraction,
		"page":         kindNullableInt,
		"pageCount":    kindNullableInt,
		"pageOffset":   kindFraction,
		"readAt":       kindDateTime,
	},
	Chapter: {
		"seriesId":     kindUUID,
		"number":       kindChapter,
		"read":         kindBool,
		"openedUrl":    kindNullableString,
		"openedDomain": kindNullableString,
	},
	LibraryEntry: {
		"seriesId": kindUUID,
		"status":   kindStatus,
		"hidden":   kindBool,
	},
}

var (
	uuidPattern    = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)
	chapterPattern = regexp.MustCompile(`^(0|[1-9][0-9]*)(\.[0-9]*[1-9])?$`)
	statuses       = map[string]bool{"reading": true, "planned": true, "paused": true, "completed": true, "dropped": true}
)

const maxStringLen = 2048

// validValue reports whether raw is a JSON value of kind k.
func validValue(k kind, raw json.RawMessage) bool {
	isNull := bytes.Equal(bytes.TrimSpace(raw), []byte("null"))
	switch k {
	case kindNullableString:
		if isNull {
			return true
		}
		var s string
		return json.Unmarshal(raw, &s) == nil && len(s) <= maxStringLen
	case kindNullableInt:
		if isNull {
			return true
		}
		var n int64
		return json.Unmarshal(raw, &n) == nil
	case kindUUID:
		var s string
		return json.Unmarshal(raw, &s) == nil && uuidPattern.MatchString(s)
	case kindBool:
		var b bool
		return json.Unmarshal(raw, &b) == nil
	case kindFraction:
		var f float64
		return json.Unmarshal(raw, &f) == nil && f >= 0 && f <= 1
	case kindDateTime:
		var s string
		if json.Unmarshal(raw, &s) != nil {
			return false
		}
		_, err := time.Parse(time.RFC3339Nano, s)
		return err == nil
	case kindChapter:
		var s string
		return json.Unmarshal(raw, &s) == nil && chapterPattern.MatchString(s)
	case kindStatus:
		var s string
		return json.Unmarshal(raw, &s) == nil && statuses[s]
	}
	return false
}
