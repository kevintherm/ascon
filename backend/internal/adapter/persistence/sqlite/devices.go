package sqlite

import (
	"context"

	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite/sqlcgen"
	"github.com/kevintherm/ascon/backend/internal/domain/device"
)

// Devices implements device.Repository.
type Devices struct{ db *DB }

// NewDevices returns the device repository.
func NewDevices(db *DB) *Devices { return &Devices{db} }

var _ device.Repository = (*Devices)(nil)

// Create stores a device and the hash of its token.
func (d *Devices) Create(ctx context.Context, dev device.Device, tokenHash []byte) error {
	return d.db.w.CreateDevice(ctx, sqlcgen.CreateDeviceParams{
		ID:             dev.ID,
		TokenHash:      tokenHash,
		AppVersion:     dev.AppVersion,
		WebviewVersion: nullString(dev.WebViewVersion),
		CreatedAt:      formatTime(dev.CreatedAt),
	})
}

// FindByTokenHash returns the device holding the token.
func (d *Devices) FindByTokenHash(ctx context.Context, tokenHash []byte) (device.Device, error) {
	row, err := d.db.r.DeviceByTokenHash(ctx, tokenHash)
	if err != nil {
		return device.Device{}, notFound(err)
	}
	created, err := parseTime(row.CreatedAt)
	if err != nil {
		return device.Device{}, err
	}
	return device.Device{
		ID:             row.ID,
		AppVersion:     row.AppVersion,
		WebViewVersion: row.WebviewVersion.String,
		CreatedAt:      created,
	}, nil
}

// Delete removes a device.
func (d *Devices) Delete(ctx context.Context, id string) error {
	return d.db.w.DeleteDevice(ctx, id)
}
