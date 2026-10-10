// Package dotenv loads KEY=value lines from a local .env file into the
// environment, for development. Variables already set win, so production
// sets everything in the real environment and needs no file.
package dotenv

import (
	"bufio"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"strings"
)

// Load reads path and sets each variable that is not already set. A missing
// file is not an error. Blank lines and lines starting with # are skipped,
// and one pair of surrounding quotes is removed from a value.
func Load(path string) error {
	f, err := os.Open(path) //nolint:gosec // The path is the server's own config file.
	if errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	defer func() { _ = f.Close() }()

	scanner := bufio.NewScanner(f)
	for n := 1; scanner.Scan(); n++ {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, value, ok := strings.Cut(strings.TrimPrefix(line, "export "), "=")
		key = strings.TrimSpace(key)
		if !ok || key == "" {
			return fmt.Errorf("%s:%d is not KEY=value", path, n)
		}
		value = unquote(strings.TrimSpace(value))
		if _, set := os.LookupEnv(key); !set {
			if err := os.Setenv(key, value); err != nil {
				return err
			}
		}
	}
	return scanner.Err()
}

func unquote(v string) string {
	if len(v) >= 2 && (v[0] == '"' || v[0] == '\'') && v[len(v)-1] == v[0] {
		return v[1 : len(v)-1]
	}
	return v
}
