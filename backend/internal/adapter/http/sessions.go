package http

import "net/http"

type signInRequest struct {
	IDToken string `json:"idToken"`
}

type sessionDTO struct {
	AccountID string `json:"accountId"`
	Token     string `json:"token"`
	Tier      string `json:"tier"`
}

func (s *Server) signIn(w http.ResponseWriter, r *http.Request) {
	var req signInRequest
	if err := decode(w, r, maxBody, &req); err != nil {
		s.fail(w, r, err)
		return
	}
	acc, token, err := s.SignIn.SignIn(r.Context(), req.IDToken)
	if err != nil {
		s.fail(w, r, err)
		return
	}
	writeJSON(w, http.StatusCreated, sessionDTO{AccountID: acc.ID, Token: token, Tier: string(acc.Tier)})
}

func (s *Server) signOut(w http.ResponseWriter, r *http.Request) {
	if err := s.SignIn.SignOut(r.Context(), bearer(r)); err != nil {
		s.fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
