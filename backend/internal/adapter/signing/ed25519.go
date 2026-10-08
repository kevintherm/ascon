// Package signing signs rule payloads with the server's Ed25519 key. The app
// pins the matching public keys and rejects any rule that fails to verify.
package signing

import (
	"crypto/ed25519"
	"fmt"

	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// Signer holds one private key and the id the app knows it by.
type Signer struct {
	key   ed25519.PrivateKey
	keyID string
}

var _ rule.Signer = (*Signer)(nil)

// New returns a signer for a 32-byte Ed25519 seed.
func New(seed []byte, keyID string) (*Signer, error) {
	if len(seed) != ed25519.SeedSize {
		return nil, fmt.Errorf("signing key seed must be %d bytes, got %d", ed25519.SeedSize, len(seed))
	}
	if keyID == "" {
		return nil, fmt.Errorf("signing key id is empty")
	}
	return &Signer{key: ed25519.NewKeyFromSeed(seed), keyID: keyID}, nil
}

// Sign signs payload.
func (s *Signer) Sign(payload []byte) ([]byte, string) {
	return ed25519.Sign(s.key, payload), s.keyID
}

// PublicKey returns the key the app pins.
func (s *Signer) PublicKey() ed25519.PublicKey {
	pub, _ := s.key.Public().(ed25519.PublicKey)
	return pub
}
