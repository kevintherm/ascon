# Contracts

Shared definitions that the Android app and the Go backend both implement. A change here is a change to both sides.

| Path | What it is |
|---|---|
| `rule.schema.json` | JSON Schema for a detection rule |
| `fixtures/conformance/` | Pages, rules and expected results that both evaluators must reproduce exactly |
| `fixtures/chapter-numbers.json` | Chapter label parsing cases |
| `fixtures/urls.json` | URL resolution cases, with expected values recorded from Chromium by `npm run record-urls` in `android/engine/detection` |

## Evaluating a rule

The JS evaluator runs inside the WebView. The Go evaluator runs on the server to check AI-generated rules before they are stored. Both take a rule, a page URL and a parsed HTML document, and both must return the same JSON for the same input. The conformance fixtures prove they do.

### Page type

1. Match the page URL against `chapterPage.url`. The pattern must match the whole URL; evaluators anchor it themselves. A match makes it a chapter page.
2. Otherwise match it against `seriesPage.url`. A match makes it a series page.
3. Otherwise the result is `{"pageType": "none"}`.

### Chapter page result

| Field | Value |
|---|---|
| `pageType` | `"chapter"` |
| `series` | The URL's `series` group, or null |
| `title` | `chapterPage.title` read as text, or null |
| `chapterLabel` | `chapterPage.chapterLabel` read as text, or null |
| `chapter` | The chapter number parsed from `chapterLabel`. If that gives nothing, parsed from the URL's `chapter` group. Otherwise null |
| `images` | One URL per element matched by `chapterPage.images.selector`, in document order. Elements without a usable URL are skipped |
| `next`, `previous` | The link's resolved URL, or null |

### Series page result

| Field | Value |
|---|---|
| `pageType` | `"series"` |
| `series` | The URL's `series` group, or null |
| `title` | `seriesPage.title` read as text, or null |
| `chapters` | One `{url, label, number}` per element matched by `chapterLinks`, in document order. `url` is the resolved `href`, `label` is the text, `number` is parsed from the label or null. Elements without a usable URL are skipped |

Every field is always present. Missing values are `null`, never absent.

### Reading values

- **Text** is the element's `textContent`. Runs of tab, line feed, form feed, carriage return, space and no-break space U+00A0 collapse to one space, and the ends are trimmed.
- **Attributes** are trimmed of the same characters.
- An empty value counts as no value.
- **`pattern`**, when present, searches the value. Unlike URL patterns it is not anchored. The named group `value` of the first match becomes the result, trimmed again. No match gives no value.
- **Selectors** pick the first matching element in document order, except for `images` and `chapterLinks`, which use every match.

### URLs

- Links and image URLs resolve against the page URL the way Chromium's `URL` does, because Chromium runs the JS evaluator in the WebView. That is the WHATWG URL standard plus Chromium's own differences; for example, Chromium percent-encodes `|` in a path. A `<base>` element is ignored.
- The fragment is dropped, so `ch-20#top` gives `ch-20`.
- Only absolute `http` and `https` results count. Anything else, such as `data:`, `blob:` or `javascript:`, counts as no value.
- For images, `attributes` are tried in order. The first one that gives a usable URL wins.
- A URL with a broken percent-escape such as `%zz`, or a scheme with no slashes such as `https:cdn.example/x`, counts as no value. Go's parser rejects them, so rules must not rely on them.
- `srcset` is split on commas. Each candidate is a URL followed by an optional `<n>w` or `<n>x` descriptor. With any `w` descriptors, the largest width wins. Otherwise the largest density wins, and a candidate without a descriptor counts as `1x`. Ties go to the first candidate. A candidate with more than one descriptor or an unknown one is skipped. URLs that contain commas are not supported.

### Chapter numbers

`parseChapterNumber` turns a label into a decimal string, or null.

1. Lowercase ASCII letters.
2. Remove volume markers: `vol` or `volume`, an optional dot, optional spaces and a number.
3. If a keyword appears, take the number after it. The keywords are `chapter`, `chap`, `ch`, `episode` and `ep`, at the start of a word. Dots, spaces, `#`, `:`, `_` and `-` may sit between the keyword and the number. The number may use `.`, `,` or `-` as its decimal separator, so `chapter-10-5` gives `10.5`.
4. Otherwise, if the whole label is a number with an optional `.`, `,` or `-` part, take it.
5. Otherwise take the first number in the label. Here only `.` and `,` count as decimal separators.
6. Normalize: the separator becomes `.`, leading zeros of the whole part go, trailing zeros of the fraction go, and an empty fraction drops its dot. `010.50` gives `10.5`, `7.0` gives `7`.

Only ASCII digits count, so full-width digits give null.

### Selector subset

Rules may use only these selector features, so that browsers and the Go selector engine agree:

- Type, `#id`, `.class` and `*`
- Attribute selectors: `[a]`, `[a=v]`, `[a~=v]`, `[a^=v]`, `[a$=v]`, `[a*=v]`
- Descendant and `>` child combinators
- Selector lists with `,`
- `:first-child`, `:last-child`, `:nth-child(n)`, `:nth-of-type(n)` and `:not()` with a simple selector inside

### Regular expressions

URL patterns and `pattern` values use the subset that RE2 and JavaScript agree on. No lookaround, no backreferences, no inline flags such as `(?i)`. Named groups are written `(?<name>...)`. Matching is case-sensitive.

## Fixtures

Each folder in `fixtures/conformance/` is one made-up site. It holds a `rule.json` and a `cases.json`. Each case names an HTML file in the folder, the URL the page was loaded from, and the exact result expected.

The sites are invented. Their markup copies the structure of common manga site themes. Image URLs point at `.example` hosts and are never fetched.
