package dotenv

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoadSetsOnlyUnsetVariables(t *testing.T) {
	path := filepath.Join(t.TempDir(), ".env")
	content := "# dev\n\nDOTENV_A=one\nexport DOTENV_B=\"two words\"\nDOTENV_C='x=y'\nDOTENV_SET=file\n"
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("DOTENV_SET", "env")
	for _, k := range []string{"DOTENV_A", "DOTENV_B", "DOTENV_C"} {
		t.Setenv(k, "")
		_ = os.Unsetenv(k)
	}

	if err := Load(path); err != nil {
		t.Fatal(err)
	}
	want := map[string]string{"DOTENV_A": "one", "DOTENV_B": "two words", "DOTENV_C": "x=y", "DOTENV_SET": "env"}
	for k, v := range want {
		if got := os.Getenv(k); got != v {
			t.Errorf("%s = %q, want %q", k, got, v)
		}
	}
}

func TestLoadIgnoresAMissingFileAndRejectsBadLines(t *testing.T) {
	dir := t.TempDir()
	if err := Load(filepath.Join(dir, "absent")); err != nil {
		t.Fatal(err)
	}
	bad := filepath.Join(dir, ".env")
	_ = os.WriteFile(bad, []byte("NOT A PAIR\n"), 0o600)
	if err := Load(bad); err == nil {
		t.Fatal("a line without = was accepted")
	}
}
