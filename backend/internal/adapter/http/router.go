// Package http holds the HTTP handlers and DTOs. Handlers translate requests
// into use case calls and never reach persistence directly.
package http

import "net/http"

// NewRouter returns the API's routes.
func NewRouter() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", healthz)
	return mux
}

func healthz(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	_, _ = w.Write([]byte("ok"))
}
