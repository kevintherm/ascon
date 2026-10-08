package registerdevice

import (
	"bytes"
	"context"
	"errors"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/device"
)

type memDevices struct {
	byHash map[string]device.Device
}

func (m *memDevices) Create(_ context.Context, d device.Device, h []byte) error {
	m.byHash[string(h)] = d
	return nil
}

func (m *memDevices) FindByTokenHash(_ context.Context, h []byte) (device.Device, error) {
	d, ok := m.byHash[string(h)]
	if !ok {
		return device.Device{}, domain.ErrNotFound
	}
	return d, nil
}

func (m *memDevices) Delete(_ context.Context, id string) error {
	for h, d := range m.byHash {
		if d.ID == id {
			delete(m.byHash, h)
		}
	}
	return nil
}

func TestRegisterAuthenticateDelete(t *testing.T) {
	ctx := context.Background()
	repo := &memDevices{byHash: map[string]device.Device{}}
	s := New(repo, func() time.Time { return time.Unix(0, 0) })

	d, token, err := s.Register(ctx, "0.1.0", "141.0")
	if err != nil {
		t.Fatal(err)
	}
	for h := range repo.byHash {
		if bytes.Contains([]byte(h), []byte(token)) {
			t.Fatal("the raw token was stored")
		}
	}

	got, err := s.Authenticate(ctx, token)
	if err != nil || got.ID != d.ID {
		t.Fatalf("Authenticate = %+v, %v", got, err)
	}
	if _, err := s.Authenticate(ctx, "wrong"); !errors.Is(err, domain.ErrUnauthenticated) {
		t.Fatalf("wrong token: %v, want ErrUnauthenticated", err)
	}

	if err := s.Delete(ctx, d.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Authenticate(ctx, token); !errors.Is(err, domain.ErrUnauthenticated) {
		t.Fatalf("after delete: %v, want ErrUnauthenticated", err)
	}
}

func TestRegisterValidates(t *testing.T) {
	s := New(&memDevices{byHash: map[string]device.Device{}}, time.Now)
	if _, _, err := s.Register(context.Background(), "", ""); !errors.Is(err, domain.ErrInvalid) {
		t.Fatalf("empty appVersion: %v, want ErrInvalid", err)
	}
}
