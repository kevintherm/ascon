package signing

import (
	"crypto/ed25519"
	"encoding/base64"
	"encoding/json"
	"os"
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

// The app verifies contracts/fixtures/signed-rule.json too, so a change on either
// side that breaks signatures fails a test.
func TestSignedRuleFixture(t *testing.T) {
	raw, err := os.ReadFile("../../../../contracts/fixtures/signed-rule.json")
	if err != nil {
		t.Fatal(err)
	}
	var f struct{ Seed, PublicKey, KeyID, Payload, Signature string }
	if err := json.Unmarshal(raw, &f); err != nil {
		t.Fatal(err)
	}
	seed, _ := base64.StdEncoding.DecodeString(f.Seed)
	s, err := New(seed, f.KeyID)
	if err != nil {
		t.Fatal(err)
	}
	payload, _ := base64.RawURLEncoding.DecodeString(f.Payload)
	sig, _ := s.Sign(payload)
	if got := base64.RawURLEncoding.EncodeToString(sig); got != f.Signature {
		t.Fatalf("signature changed: %s", got)
	}
	if got := base64.StdEncoding.EncodeToString(s.PublicKey()); got != f.PublicKey {
		t.Fatalf("public key changed: %s", got)
	}
}
