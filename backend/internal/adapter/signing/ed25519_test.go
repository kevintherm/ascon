package signing

import (
	"crypto/ed25519"
	"testing"
)

func TestSignVerifies(t *testing.T) {
	s, err := New(make([]byte, ed25519.SeedSize), "2026-10")
	if err != nil {
		t.Fatal(err)
	}
	payload := []byte(`{"domain":"a.example"}`)
	sig, keyID := s.Sign(payload)
	if keyID != "2026-10" || !ed25519.Verify(s.PublicKey(), payload, sig) {
		t.Fatal("signature does not verify")
	}
	if ed25519.Verify(s.PublicKey(), []byte(`{"domain":"b.example"}`), sig) {
		t.Fatal("signature verifies a different payload")
	}
}

func TestNewRejectsBadSeed(t *testing.T) {
	if _, err := New([]byte("short"), "k"); err == nil {
		t.Fatal("short seed accepted")
	}
}
