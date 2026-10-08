// Package registerdevice registers anonymous devices and checks their tokens.
package registerdevice

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/device"
)

// Service registers devices.
type Service struct {
	devices device.Repository
	now     func() time.Time
}

// New returns the service.
func New(devices device.Repository, now func() time.Time) *Service {
	return &Service{devices: devices, now: now}
}

// Register creates a device and returns it with its bearer token. Only the
// token's hash is stored.
func (s *Service) Register(ctx context.Context, appVersion, webViewVersion string) (device.Device, string, error) {
	if appVersion == "" || len(appVersion) > 32 {
		return device.Device{}, "", domain.Invalidf("appVersion must be 1 to 32 characters")
	}
	if len(webViewVersion) > 64 {
		return device.Device{}, "", domain.Invalidf("webViewVersion must be at most 64 characters")
	}

	var raw [32]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return device.Device{}, "", err
	}
	token := base64.RawURLEncoding.EncodeToString(raw[:])

	d := device.Device{ID: domain.NewID(), AppVersion: appVersion, WebViewVersion: webViewVersion, CreatedAt: s.now()}
	if err := s.devices.Create(ctx, d, hash(token)); err != nil {
		return device.Device{}, "", err
	}
	return d, token, nil
}

// Authenticate returns the device holding token, or domain.ErrUnauthenticated.
func (s *Service) Authenticate(ctx context.Context, token string) (device.Device, error) {
	if token == "" {
		return device.Device{}, domain.ErrUnauthenticated
	}
	d, err := s.devices.FindByTokenHash(ctx, hash(token))
	if errors.Is(err, domain.ErrNotFound) {
		return device.Device{}, domain.ErrUnauthenticated
	}
	return d, err
}

// Delete removes the device and everything stored for it.
func (s *Service) Delete(ctx context.Context, deviceID string) error {
	return s.devices.Delete(ctx, deviceID)
}

func hash(token string) []byte {
	h := sha256.Sum256([]byte(token))
	return h[:]
}
