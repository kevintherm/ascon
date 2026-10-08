package evaluator

import (
	"regexp"
	"strconv"
	"strings"
)

var descriptor = regexp.MustCompile(`^(\d+(?:\.\d+)?)([wx])$`)

// largestCandidate picks the srcset candidate with the largest width, or the
// largest density when no candidate gives a width. Ties go to the first.
func largestCandidate(srcset string) *string {
	var bestW, bestX string
	maxW, maxX := -1.0, -1.0

	for _, c := range strings.Split(srcset, ",") {
		fields := strings.FieldsFunc(c, func(r rune) bool { return strings.ContainsRune(whitespace, r) })
		if len(fields) == 0 || len(fields) > 2 {
			continue
		}
		size, kind := 1.0, "x"
		if len(fields) == 2 {
			m := descriptor.FindStringSubmatch(fields[1])
			if m == nil {
				continue
			}
			size, _ = strconv.ParseFloat(m[1], 64)
			kind = m[2]
		}
		if kind == "w" && size > maxW {
			bestW, maxW = fields[0], size
		}
		if kind == "x" && size > maxX {
			bestX, maxX = fields[0], size
		}
	}

	switch {
	case maxW >= 0:
		return &bestW
	case maxX >= 0:
		return &bestX
	default:
		return nil
	}
}
