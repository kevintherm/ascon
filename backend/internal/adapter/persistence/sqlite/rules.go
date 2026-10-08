package sqlite

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"

	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite/sqlcgen"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// Rules implements rule.Repository. Each version's rule is stored as JSON.
type Rules struct{ db *DB }

// NewRules returns the rule repository.
func NewRules(db *DB) *Rules { return &Rules{db} }

var _ rule.Repository = (*Rules)(nil)

// Latest returns the newest version for domain that is not retired.
func (r *Rules) Latest(ctx context.Context, domain string) (rule.Stored, error) {
	row, err := r.db.r.LatestRule(ctx, domain)
	if err != nil {
		return rule.Stored{}, notFound(err)
	}
	return stored(row.Body, row.Status, row.CreatedAt)
}

// Get returns one version.
func (r *Rules) Get(ctx context.Context, domain string, version int) (rule.Stored, error) {
	row, err := r.db.r.GetRule(ctx, sqlcgen.GetRuleParams{Domain: domain, Version: int64(version)})
	if err != nil {
		return rule.Stored{}, notFound(err)
	}
	return stored(row.Body, row.Status, row.CreatedAt)
}

// Fingerprinted lists the newest non-retired version of every fingerprinted rule.
func (r *Rules) Fingerprinted(ctx context.Context) ([]rule.Stored, error) {
	rows, err := r.db.r.FingerprintedRules(ctx)
	if err != nil {
		return nil, err
	}
	out := make([]rule.Stored, 0, len(rows))
	for _, row := range rows {
		s, err := stored(row.Body, row.Status, row.CreatedAt)
		if err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, nil
}

// MaxVersion returns the highest version ever stored for domain, or 0.
func (r *Rules) MaxVersion(ctx context.Context, domain string) (int, error) {
	v, err := r.db.r.MaxRuleVersion(ctx, domain)
	return int(v), err
}

// Insert stores a new version.
func (r *Rules) Insert(ctx context.Context, s rule.Stored) error {
	body, err := json.Marshal(s.Rule)
	if err != nil {
		return err
	}
	return r.db.w.InsertRule(ctx, sqlcgen.InsertRuleParams{
		Domain:      s.Rule.Domain,
		Version:     int64(s.Rule.Version),
		Body:        body,
		Fingerprint: nullString(s.Rule.Fingerprint),
		Status:      string(s.Status),
		CreatedAt:   formatTime(s.CreatedAt),
	})
}

// SetStatus changes a version's status.
func (r *Rules) SetStatus(ctx context.Context, domain string, version int, status rule.Status) error {
	return r.db.w.SetRuleStatus(ctx, sqlcgen.SetRuleStatusParams{Status: string(status), Domain: domain, Version: int64(version)})
}

// Health returns a version's counts, zero when none were sent.
func (r *Rules) Health(ctx context.Context, domain string, version int) (rule.Health, error) {
	row, err := r.db.r.RuleHealth(ctx, sqlcgen.RuleHealthParams{Domain: domain, Version: int64(version)})
	if errors.Is(err, sql.ErrNoRows) {
		return rule.Health{}, nil
	}
	if err != nil {
		return rule.Health{}, err
	}
	return rule.Health{Successes: int(row.Successes), EmptyResults: int(row.EmptyResults), BackwardJumps: int(row.BackwardJumps)}, nil
}

// AddHealth adds to a version's counts.
func (r *Rules) AddHealth(ctx context.Context, domain string, version int, h rule.Health) (rule.Health, error) {
	row, err := r.db.w.AddRuleHealth(ctx, sqlcgen.AddRuleHealthParams{
		Domain: domain, Version: int64(version),
		Successes: int64(h.Successes), EmptyResults: int64(h.EmptyResults), BackwardJumps: int64(h.BackwardJumps),
	})
	if err != nil {
		return rule.Health{}, err
	}
	return rule.Health{Successes: int(row.Successes), EmptyResults: int(row.EmptyResults), BackwardJumps: int(row.BackwardJumps)}, nil
}

// AddReport records a report and counts the devices that reported the version.
func (r *Rules) AddReport(ctx context.Context, rep rule.Report) (int, error) {
	var devices int64
	err := r.db.inTx(ctx, func(q *sqlcgen.Queries) error {
		err := q.AddRuleReport(ctx, sqlcgen.AddRuleReportParams{
			Domain: rep.Domain, Version: int64(rep.Version), DeviceID: rep.DeviceID,
			Problem: rep.Problem, Url: rep.URL, Correction: nullString(string(rep.Correction)),
			CreatedAt: formatTime(rep.CreatedAt),
		})
		if err != nil {
			return err
		}
		devices, err = q.ReportingDevices(ctx, sqlcgen.ReportingDevicesParams{Domain: rep.Domain, Version: int64(rep.Version)})
		return err
	})
	return int(devices), err
}

func stored(body []byte, status, createdAt string) (rule.Stored, error) {
	var r rule.Rule
	if err := json.Unmarshal(body, &r); err != nil {
		return rule.Stored{}, err
	}
	created, err := parseTime(createdAt)
	if err != nil {
		return rule.Stored{}, err
	}
	return rule.Stored{Rule: r, Status: rule.Status(status), CreatedAt: created}, nil
}
