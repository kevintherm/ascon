// Package domain holds entities and repository interfaces. It imports nothing
// outside the standard library; every other layer depends on it.
package domain

import (
	"crypto/rand"
	"errors"
	"fmt"
)

// Errors shared by every repository and use case.
var (
	ErrNotFound        = errors.New("not found")
	ErrUnauthenticated = errors.New("unauthenticated")
	ErrConflict        = errors.New("conflict")
	ErrInvalid         = errors.New("invalid")
)

// Invalidf wraps ErrInvalid with a message a client can act on.
func Invalidf(format string, args ...any) error {
	return fmt.Errorf("%w: %s", ErrInvalid, fmt.Sprintf(format, args...))
}

// NewID returns a random version 4 UUID.
func NewID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(err) // crypto/rand never fails on supported platforms
	}
	b[6] = b[6]&0x0f | 0x40
	b[8] = b[8]&0x3f | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
