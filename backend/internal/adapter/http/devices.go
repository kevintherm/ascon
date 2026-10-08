package http

import "net/http"

type deviceRegistration struct {
	AppVersion     string `json:"appVersion"`
	WebViewVersion string `json:"webViewVersion,omitempty"`
}

type deviceDTO struct {
	DeviceID string `json:"deviceId"`
	Token    string `json:"token"`
}

func (s *Server) registerDevice(w http.ResponseWriter, r *http.Request) {
	var req deviceRegistration
	if err := decode(w, r, maxBody, &req); err != nil {
		s.fail(w, r, err)
		return
	}
	d, token, err := s.Devices.Register(r.Context(), req.AppVersion, req.WebViewVersion)
	if err != nil {
		s.fail(w, r, err)
		return
	}
	writeJSON(w, http.StatusCreated, deviceDTO{DeviceID: d.ID, Token: token})
}

func (s *Server) deleteDevice(w http.ResponseWriter, r *http.Request) {
	if err := s.Devices.Delete(r.Context(), deviceFrom(r).ID); err != nil {
		s.fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
