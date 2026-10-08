package library

import (
	"encoding/json"
	"testing"
	"time"
)

type memStore struct {
	fields     map[string]FieldValue
	tombstones map[string]time.Time
	seq        int64
}

func newMemStore() *memStore {
	return &memStore{fields: map[string]FieldValue{}, tombstones: map[string]time.Time{}}
}

func (m *memStore) Field(e, id, f string) (FieldValue, bool, error) {
	v, ok := m.fields[e+"/"+id+"/"+f]
	return v, ok, nil
}

func (m *memStore) PutField(e, id, f string, v FieldValue, _ int64) error {
	m.fields[e+"/"+id+"/"+f] = v
	return nil
}

func (m *memStore) Tombstone(e, id string) (time.Time, bool, error) {
	t, ok := m.tombstones[e+"/"+id]
	return t, ok, nil
}

func (m *memStore) PutTombstone(e, id string, at time.Time, _ int64) error {
	m.tombstones[e+"/"+id] = at
	return nil
}

func (m *memStore) NextSeq() (int64, error) { m.seq++; return m.seq, nil }

const id = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"

var (
	t0  = time.Date(2026, 10, 8, 10, 0, 0, 0, time.UTC)
	now = t0.Add(time.Hour)
)

func field(value string, at time.Time) FieldValue {
	return FieldValue{Value: json.RawMessage(value), UpdatedAt: at}
}

func TestMergeLaterWritePerField(t *testing.T) {
	s := newMemStore()
	mustMerge(t, s, Change{Entity: Progress, ID: id, Fields: map[string]FieldValue{
		"chapter":      field(`"10"`, t0.Add(2*time.Minute)),
		"pagePosition": field(`0.5`, t0),
	}})
	// Another device read further on an older chapter value but moved the
	// position later.
	mustMerge(t, s, Change{Entity: Progress, ID: id, Fields: map[string]FieldValue{
		"chapter":      field(`"9"`, t0.Add(time.Minute)),
		"pagePosition": field(`0.8`, t0.Add(3*time.Minute)),
	}})

	if got := string(s.fields[Progress+"/"+id+"/chapter"].Value); got != `"10"` {
		t.Errorf("chapter = %s, want the later write \"10\"", got)
	}
	if got := string(s.fields[Progress+"/"+id+"/pagePosition"].Value); got != `0.8` {
		t.Errorf("pagePosition = %s, want the later write 0.8", got)
	}
}

func TestMergeTieTakesLargerValue(t *testing.T) {
	s := newMemStore()
	mustMerge(t, s, Change{Entity: Series, ID: id, Fields: map[string]FieldValue{"title": field(`"B"`, t0)}})
	mustMerge(t, s, Change{Entity: Series, ID: id, Fields: map[string]FieldValue{"title": field(`"A"`, t0)}})
	if got := string(s.fields[Series+"/"+id+"/title"].Value); got != `"B"` {
		t.Errorf("title = %s, want \"B\" on a tie", got)
	}
}

func TestMergeClampsFutureTimestamps(t *testing.T) {
	s := newMemStore()
	mustMerge(t, s, Change{Entity: Series, ID: id, Fields: map[string]FieldValue{"title": field(`"From the future"`, now.Add(24*time.Hour))}})
	if got := s.fields[Series+"/"+id+"/title"].UpdatedAt; !got.Equal(now) {
		t.Errorf("updatedAt = %v, want clamped to %v", got, now)
	}
}

func TestMergeKeepsLaterTombstone(t *testing.T) {
	s := newMemStore()
	later, earlier := t0.Add(time.Minute), t0
	mustMerge(t, s, Change{Entity: LibraryEntry, ID: id, Deleted: &later})
	mustMerge(t, s, Change{Entity: LibraryEntry, ID: id, Deleted: &earlier})
	if got := s.tombstones[LibraryEntry+"/"+id]; !got.Equal(later) {
		t.Errorf("tombstone = %v, want %v", got, later)
	}
}

func TestValidate(t *testing.T) {
	cases := []struct {
		name string
		c    Change
		ok   bool
	}{
		{"valid progress", Change{Entity: Progress, ID: id, Fields: map[string]FieldValue{"chapter": field(`"10.5"`, t0)}}, true},
		{"unknown entity", Change{Entity: "page", ID: id, Fields: map[string]FieldValue{"x": field(`1`, t0)}}, false},
		{"bad id", Change{Entity: Series, ID: "7", Fields: map[string]FieldValue{"title": field(`"x"`, t0)}}, false},
		{"unknown field", Change{Entity: Series, ID: id, Fields: map[string]FieldValue{"cover": field(`"x"`, t0)}}, false},
		{"chapter not normalized", Change{Entity: Progress, ID: id, Fields: map[string]FieldValue{"chapter": field(`"10.50"`, t0)}}, false},
		{"position out of range", Change{Entity: Progress, ID: id, Fields: map[string]FieldValue{"pagePosition": field(`1.5`, t0)}}, false},
		{"unknown status", Change{Entity: LibraryEntry, ID: id, Fields: map[string]FieldValue{"status": field(`"lost"`, t0)}}, false},
		{"null title", Change{Entity: Series, ID: id, Fields: map[string]FieldValue{"title": field(`null`, t0)}}, true},
		{"empty", Change{Entity: Series, ID: id}, false},
	}
	for _, c := range cases {
		if err := c.c.Validate(); (err == nil) != c.ok {
			t.Errorf("%s: Validate() = %v, want ok=%v", c.name, err, c.ok)
		}
	}
}

func TestGroup(t *testing.T) {
	rows := []Row{
		{Entity: Series, ID: "a", Field: "title", Value: json.RawMessage(`"A"`), UpdatedAt: t0, Seq: 1},
		{Entity: Series, ID: "b", Field: "", UpdatedAt: t0, Seq: 2},
		{Entity: Series, ID: "a", Field: "anilistId", Value: json.RawMessage(`7`), UpdatedAt: t0, Seq: 3},
	}
	got := Group(rows)
	if len(got) != 2 || got[0].ID != "a" || len(got[0].Fields) != 2 || got[1].Deleted == nil {
		t.Errorf("Group = %+v", got)
	}
}

func mustMerge(t *testing.T, s Store, c Change) {
	t.Helper()
	if _, err := Merge(s, []Change{c}, now); err != nil {
		t.Fatal(err)
	}
}
