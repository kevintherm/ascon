package rule

import "testing"

func TestFingerprintDistance(t *testing.T) {
	cases := []struct {
		a, b string
		want int
	}{
		{"0000000000000000", "0000000000000000", 0},
		{"0000000000000000", "0000000000000001", 1},
		{"ffffffffffffffff", "0000000000000000", 64},
		{"9f0c2a7e41b3d856", "9f0c2a7e41b3d857", 1},
		{"9f0c2a7e41b3d856", "short", -1},
		{"zzzzzzzzzzzzzzzz", "0000000000000000", -1},
	}
	for _, c := range cases {
		if got := FingerprintDistance(c.a, c.b); got != c.want {
			t.Errorf("FingerprintDistance(%q, %q) = %d, want %d", c.a, c.b, got, c.want)
		}
	}
}

func TestConfidence(t *testing.T) {
	if got := (Health{}).Confidence(); got != 0.5 {
		t.Errorf("no data: confidence = %v, want 0.5", got)
	}
	if got := (Health{Successes: 98}).Confidence(); got != 0.99 {
		t.Errorf("98 successes: confidence = %v, want 0.99", got)
	}
	if got := (Health{EmptyResults: 4, BackwardJumps: 4}).Confidence(); got != 0.1 {
		t.Errorf("8 failures: confidence = %v, want 0.1", got)
	}
}
