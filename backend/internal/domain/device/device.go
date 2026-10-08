// Package device holds anonymous app installs. A device has no personal data
// beyond what release alerts store; see docs/adr/0001.
package device

import (
	"context"
	"time"
)

// Device is one app install.
type Device struct {
	ID             string
	AppVersion     string
	WebViewVersion string
	CreatedAt      time.Time
}

// Repository stores devices. Tokens are stored only as hashes.
type Repository interface {
	Create(ctx context.Context, d Device, tokenHash []byte) error
	// FindByTokenHash returns domain.ErrNotFound for an unknown token.
	FindByTokenHash(ctx context.Context, tokenHash []byte) (Device, error)
	Delete(ctx context.Context, id string) error
}
