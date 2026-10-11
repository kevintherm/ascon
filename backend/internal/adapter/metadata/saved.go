package metadata

import (
	"bytes"
	"embed"
	"encoding/json"
	"errors"
	"io"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"unicode"
)

// savedFiles are AniList and MangaUpdates answers saved once by
// cmd/devmetadata, at saved/<service>/<query slug>.json.
//
//go:embed saved
var savedFiles embed.FS

// Saved answers requests to AniList and MangaUpdates from the saved files
// instead of the network. A query with no saved answer gets an empty result,
// as from a service that found nothing. Tests and the dev backend use it, so
// neither ever reaches the services.
type Saved struct{}

// RoundTrip implements http.RoundTripper.
func (Saved) RoundTrip(req *http.Request) (*http.Response, error) {
	service, query, err := savedKey(req)
	if err != nil {
		return nil, err
	}
	body, err := fs.ReadFile(savedFiles, "saved/"+service+"/"+Slug(query)+".json")
	if errors.Is(err, fs.ErrNotExist) {
		body, err = []byte(emptyAnswers[service]), nil
	}
	if err != nil {
		return nil, err
	}
	return &http.Response{
		StatusCode: http.StatusOK,
		Header:     http.Header{"Content-Type": {"application/json"}},
		Body:       io.NopCloser(bytes.NewReader(body)),
		Request:    req,
	}, nil
}

var emptyAnswers = map[string]string{
	"anilist":      `{"data":{"Page":{"media":[]}}}`,
	"mangaupdates": `{"total_hits":0,"results":[]}`,
}

// savedKey reads which service a request goes to and the title it searches.
func savedKey(req *http.Request) (service, query string, err error) {
	var body struct {
		Search    string `json:"search"`
		Variables struct {
			Search string `json:"search"`
		} `json:"variables"`
	}
	if req.Body != nil {
		data, readErr := io.ReadAll(req.Body)
		if readErr != nil {
			return "", "", readErr
		}
		req.Body = io.NopCloser(bytes.NewReader(data))
		if err := json.Unmarshal(data, &body); err != nil {
			return "", "", err
		}
	}
	switch {
	case strings.HasSuffix(req.URL.Host, "anilist.co"):
		return "anilist", body.Variables.Search, nil
	case strings.HasSuffix(req.URL.Host, "mangaupdates.com"):
		return "mangaupdates", body.Search, nil
	}
	return "", "", errors.New("metadata: no saved answers for " + req.URL.Host)
}

// Slug is a query's file name: lowercase letters and digits joined by dashes.
func Slug(query string) string {
	words := strings.FieldsFunc(strings.ToLower(query), func(r rune) bool {
		return !unicode.IsLetter(r) && !unicode.IsDigit(r)
	})
	return strings.Join(words, "-")
}

// Recording sends requests on through Next and saves each answer where Saved
// will find it, under Dir. Only cmd/devmetadata uses it, by hand.
type Recording struct {
	Next http.RoundTripper
	Dir  string
}

// RoundTrip implements http.RoundTripper.
func (r Recording) RoundTrip(req *http.Request) (*http.Response, error) {
	service, query, err := savedKey(req)
	if err != nil {
		return nil, err
	}
	resp, err := r.Next.RoundTrip(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		return resp, err
	}
	data, err := io.ReadAll(resp.Body)
	_ = resp.Body.Close()
	if err != nil {
		return nil, err
	}
	resp.Body = io.NopCloser(bytes.NewReader(data))

	var pretty bytes.Buffer
	if err := json.Indent(&pretty, data, "", "  "); err != nil {
		return nil, err
	}
	pretty.WriteByte('\n')
	path := filepath.Join(r.Dir, service, Slug(query)+".json")
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return nil, err
	}
	return resp, os.WriteFile(path, pretty.Bytes(), 0o600)
}
