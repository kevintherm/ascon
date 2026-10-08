package evaluator

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/santhosh-tekuri/jsonschema/v6"

	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

const contracts = "../../../../contracts"

type fixtureCase struct {
	Name     string          `json:"name"`
	URL      string          `json:"url"`
	HTML     string          `json:"html"`
	Expected json.RawMessage `json:"expected"`
}

func TestConformance(t *testing.T) {
	schema := compileSchema(t)

	dirs, err := filepath.Glob(filepath.Join(contracts, "fixtures/conformance/*"))
	if err != nil || len(dirs) == 0 {
		t.Fatalf("no fixtures found: %v", err)
	}

	for _, dir := range dirs {
		ruleJSON := readFile(t, filepath.Join(dir, "rule.json"))

		doc, err := jsonschema.UnmarshalJSON(bytes.NewReader(ruleJSON))
		if err != nil {
			t.Fatal(err)
		}
		if err := schema.Validate(doc); err != nil {
			t.Errorf("%s/rule.json does not match the schema: %v", filepath.Base(dir), err)
			continue
		}

		var r rule.Rule
		if err := json.Unmarshal(ruleJSON, &r); err != nil {
			t.Fatal(err)
		}
		var cases []fixtureCase
		if err := json.Unmarshal(readFile(t, filepath.Join(dir, "cases.json")), &cases); err != nil {
			t.Fatal(err)
		}

		for _, c := range cases {
			t.Run(filepath.Base(dir)+"/"+c.Name, func(t *testing.T) {
				res, err := Evaluate(r, c.URL, readFile(t, filepath.Join(dir, c.HTML)))
				if err != nil {
					t.Fatal(err)
				}
				got, err := json.Marshal(res)
				if err != nil {
					t.Fatal(err)
				}
				if !sameJSON(t, got, c.Expected) {
					t.Errorf("result differs\n got: %s\nwant: %s", got, c.Expected)
				}
			})
		}
	}
}

func compileSchema(t *testing.T) *jsonschema.Schema {
	t.Helper()
	c := jsonschema.NewCompiler()
	s, err := c.Compile(filepath.Join(contracts, "rule.schema.json"))
	if err != nil {
		t.Fatal(err)
	}
	return s
}

func sameJSON(t *testing.T, a, b []byte) bool {
	t.Helper()
	var x, y any
	if err := json.Unmarshal(a, &x); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(b, &y); err != nil {
		t.Fatal(err)
	}
	return reflect.DeepEqual(x, y)
}

func readFile(t *testing.T, path string) []byte {
	t.Helper()
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return b
}
