# Ascon

Read this whole file before writing code. It records decisions already made with the owner. Do not relitigate them without asking.

## What Ascon is

An Android app that tracks manga, manhwa and comic reading across any website. It is a browser with a memory: the user reads on whatever site they like, and Ascon detects the series and chapter, saves progress, and builds the library by itself.

Ascon never hosts, mirrors or redistributes content.

**Core loop:** open a site → Ascon detects series and chapter → progress is saved → user resumes from the library, on any source.

**Differentiators** against MangaPin and MangaX, which the owner tested:

1. One series entity, many sources. The same title on two sites or two translations is one library entry. MangaPin gets this wrong.
2. AI-generated detection rules shared across users, so any site works without hand-written scrapers.
3. UI and UX quality is a hard requirement. MangaX's confusing gestures, poor hierarchy and murky dark UI are the anti-reference. Nothing ships that looks generic or "slop".

## Hard rules

- **Content stays on the device. Only metadata goes to the server.** Allowed on the server: URLs, detection rules, series metadata, progress numbers, page fingerprints. Never on the server: page images, downloaded chapters, OCR text, translated text.
- **Translation is fresh per user** and cached on the device only. No shared translation cache, no shared bubble-position cache.
- **AI never produces executable code that runs for other users.** Detection rules are declarative JSON interpreted by fixed code.
- Store listing positions Ascon as a reading tracker with privacy protection. Never market piracy sites.
- If a translation or LLM provider is used, its terms must not retain inputs, and the privacy policy says so.

## Stack

| Layer | Choice |
|---|---|
| Android app | Kotlin, Jetpack Compose, single activity |
| Web engine | Android System WebView with `androidx.webkit`. Not GeckoView, not bundled Chromium |
| Adblock | Brave's `adblock-rust` crate, built with `cargo-ndk`, bound to Kotlin with UniFFI |
| Networking | OkHttp with DNS-over-HTTPS |
| Local data | Room |
| Settings | Jetpack DataStore |
| Background work | WorkManager |
| Images | Tiled decoding for tall strips, e.g. SubsamplingScaleImageView or a Compose equivalent |
| Backend | Go, strict clean architecture |
| Backend DB | SQLite via `modernc.org/sqlite`, WAL mode, busy timeout, single writer connection |
| Backend tooling | stdlib `net/http` routing, sqlc, goose migrations, Litestream for backups, `go-arch-lint` |
| Hosting | One small VPS, 2 GB RAM and 2 vCPU. One Go binary and one SQLite file, so keep memory use and background work modest |
| iOS | Not now. Kotlin Multiplatform later for shared core if it proves worth it |

Owner's background: strong in PHP/Laravel, Flutter and Vue; learning Go and JVM languages toward a Java career. Prefer idiomatic, explainable code over clever code.

## Repository layout

```
android/            Gradle project
  app/
  core/             models, design system, utilities
  feature/library/
  feature/series/
  feature/browser/
  feature/reader/
  feature/settings/
  engine/adblock/   Rust crate + UniFFI bindings
  engine/detection/ rule evaluator JS asset + Kotlin host
backend/            Go module
contracts/          OpenAPI spec, detection rule JSON Schema, conformance fixtures
design/             tokens.md, approved screens, brand assets
docs/               ADRs and longer notes
```

## Android architecture

- Unidirectional data flow: ViewModel exposes `StateFlow<UiState>`, UI sends events.
- Modules depend inward on `core`. Features never depend on each other; navigation is wired in `app`.
- Compose theme is built from `design/tokens.md`. Material 3 is plumbing only: every color, shape and type style is overridden. Stock M3 look is a bug.

### Browser layer

- Inject scripts with `WebViewCompat.addDocumentStartJavaScript` so they run before page scripts.
- Bridge with `WebViewCompat.addWebMessageListener`, origin-restricted. Do not use `addJavascriptInterface`.
- Strip the `X-Requested-With` header with `WebSettingsCompat.setRequestedWithHeaderOriginAllowList(emptySet())`. WebView 153 reports that switch unsupported and still sends `X-Requested-With: com.ascon.app`. Decided with the owner: solve it in step 7, where adblock already routes every request through `shouldInterceptRequest`.
- Navigation guard in `shouldOverrideUrlLoading`: block `intent://`, `market://` and other non-web schemes; block cross-domain navigations where `request.hasGesture()` is false; deny `onCreateWindow`; override `window.open`.
- Click hijacking: a tap counts for a cross-domain navigation only when the navigation goes to the link the user tapped. The injected script reports the link under each tap. A site's click handler that sends the page elsewhere is blocked like a redirect.
- Block APK and executable downloads in the download listener.
- Decided with the owner: the browser is single-tab, with one live WebView. Opening a new site replaces the page, and the previous page stays in back history.
- Pre-warm one WebView at app start; first init is slow.
- Check `WebViewCompat.getCurrentWebViewPackage()` and warn on very old versions.

### Adblock

- Parse filter lists once, serialize the engine to disk, load from the snapshot on start.
- The engine is `engine/adblock/rust`, a thin UniFFI wrapper over adblock-rust that Gradle builds with cargo-ndk for arm64-v8a, armeabi-v7a and x86_64, the app's only ABIs. The Kotlin bindings are generated into the build directory and call the library through JNA. Rust changes get `cargo fmt`, `cargo clippy` and `cargo test` with the gates.
- At launch, request checks and cosmetic answers off the main thread wait up to 3 seconds for the engine, so the first page is filtered too. The main thread never waits. On a device the engine can't load on, nothing is blocked and browsing still works.
- Cosmetic hiding has its own bridge, `asconAdblock`, and script, `engine/adblock/src/main/assets/cosmetic.js`, separate from detection. Main frames only for now.
- Debug builds add `app/src/debug/assets/adblock/maestro.txt`, a list the Maestro flows block against.
- Network blocking: every request in `shouldInterceptRequest` goes through the engine. Infer resource type from main-frame flag, `Accept` header and extension. Blocked requests return an empty response.
- Cosmetic blocking: on page start, inject the engine's hide selectors and scriptlets; a `MutationObserver` reports new class and id names back for generic hiding.
- Main-frame navigations also go through the engine in `shouldOverrideUrlLoading`, tapped or not, so a link to a known ad or popunder domain is blocked. This covers hijacked links the tap rule lets through, including tapped `target=_blank` links, which load in the same tab.
- Invisible links laid over the page are left to cosmetic filters, since tapping one really is a tap on that link.
- Filter lists are modules with id, version and toggle: EasyList, EasyPrivacy, an Ascon manga-site list, per-site allowlist as generated exception rules. Toggling rebuilds the engine in the background.
- Decided with the owner: EasyList and EasyPrivacy ship in the APK, so blocking works on first launch and offline, and a WorkManager job on unmetered network downloads newer copies, weekly until item 13 of the UI fixes moves it to every 4 days with ETag. A download replaces a copy only when it is a valid list with a higher `! Version:`. Refresh the shipped copies with `android/tools/update-filter-lists.sh` before a release.
- EasyList and EasyPrivacy are dual licensed GPLv3 and CC BY-SA 3.0. The app must credit them, under CC BY-SA, on the open source licenses screen when it is built.

### Detection

**Rule format**, JSON, one per site, schema in `contracts/`:

- `chapterPage`: URL regex with named groups for series slug and chapter; selectors for title, chapter label, image container, next and previous links.
- `seriesPage`: selector for the chapter list, used by new-chapter checks.
- Metadata: version, confidence, structure fingerprint.

**Evaluator:** a small interpreter shipped inside the injected JS. Rules are data; the interpreter never changes remotely. Results go back over the bridge.

**Lookup order on each page:**

1. Local rule cache by domain
2. Backend rule by domain, then by structure fingerprint
3. Built-in hand-written rules for common WordPress manga themes
4. Heuristics: JSON-LD, `og:title`, chapter patterns in the URL
5. AI generation, last resort, automatic for a signed-in user with quota left. Otherwise the user gets heuristics and the fix-detection sheet

**AI generation, server side:**

- Client sends sanitized snapshots: scripts and styles removed, only `class`, `id`, `href`, `src`, `data-src` attributes kept, text truncated, size capped. Ideally two chapter pages from the same site.
- LLM returns a rule constrained to the JSON Schema.
- Server validates with a Go port of the evaluator: titles must match across samples, chapter numbers must parse and increase, enough images must be found. Only then is the rule stored.
- The JS and Go evaluators share one conformance suite in `contracts/fixtures/` so they cannot drift.
- `singleflight` dedupes concurrent generation for the same domain.

**Health:** successful extractions raise confidence; empty results or backward chapter jumps lower it. Below a threshold the rule is suspect and regenerated, keeping the previous version for rollback. Users are asked only when a rule is suspect, and several reports are needed before regeneration so one confused user cannot break a working rule.

**Fingerprint:** simhash of the generator meta tag, theme class names and DOM skeleton, so a new mirror domain inherits an existing rule.

### Series identity

- Tables: `series` with optional AniList and MangaUpdates ids, `source` linking a series to a site URL, `chapter` with a decimal number, `progress`.
- **Progress is keyed by series and chapter number, not by source.** Switching sources keeps the place.
- Matching: normalize the detected title, search AniList and MangaUpdates through the backend, fuzzy-match against all alternate titles, auto-link above a threshold, otherwise show the fix-detection sheet.
- Chapter parsing handles decimals like 10.5, volume prefixes, extras and split chapters.
- Decided with the owner: until the backend's metadata search exists, a detected chapter with a number adds its series to the library by itself, matched to existing series by exact title key only. The site becomes one of the series' sources. Debug builds seed the sample library into an empty database; release builds start empty.
- On the device the library lives in Room, in `core/data/room`. Rules for how reading changes a series are plain functions in `core/data/LibraryChanges.kt`, shared by Room and the fake. Schemas are exported to `core/schemas`; every schema change after version 1 needs a migration and a test against the previous schema.

### Reader mode

- Decided with the owner: the reader opens by itself when a chapter's pages are found. Back returns to the site page, and a page the reader already showed stays on the site.
- Image URLs from the rule's image selector, resolving `data-src` and `srcset`. Fallback: collect large image requests seen in `shouldInterceptRequest`.
- Without a rule, heuristics take the largest run of images sharing one container, and the next and previous chapters from links that differ from the page URL only in the chapter number.
- Fetch natively with OkHttp using the WebView's cookies from `CookieManager`, the chapter URL as `Referer`, and the exact same User-Agent, because Cloudflare clearance is tied to it.
- **If extraction fails**, zero images, blob or canvas images, or scrambled tiles: show the page as-is in the WebView, track progress with an injected `IntersectionObserver`, and show the "reader mode isn't available here" banner.
- Preload the next chapter in an off-screen WebView.
- **Planned, agreed with the owner: page lists from the site's own data.** Some readers never put all page URLs in the DOM. MangaFire keeps an empty slot per page and loads its list from `/api/chapters/<id>` with a signed `vrf` token. Spike reading such responses from the injected script, by watching `fetch` and XHR for a JSON list of image URLs, then decide. Weigh how visible the hook is to the site and whether responses are encoded. Until then these sites use the page-as-is fallback.

### Downloads

WorkManager jobs save chapters as CBZ with `ComicInfo.xml` in app-private storage. Never synced to the server.

### New-chapter alerts

- Server: poll MangaDex and MangaUpdates release feeds for canonical series in any library, deduplicated, push via FCM.
- Device: periodic WorkManager job on Wi-Fi and charging, jittered, rate-limited per domain. Loads each remaining source's series page, applies the `seriesPage` rule, diffs, posts a local notification.

### Translation, v2, premium

On-device bubble detection with LiteRT, OCR with ML Kit, translation via ML Kit on-device for free tier or a cloud LLM for premium with the whole page's text in one request, overlay drawn in Compose over the native reader, cache on device keyed by image hash. Native reader only at first.

## Backend

Strict clean architecture. Dependencies point inward only; enforce with `go-arch-lint` in CI.

```
backend/
  cmd/api/                 wiring only: config, dependency injection, server start
  internal/
    domain/                entities and repository interfaces, no external imports
      rule/ series/ library/
    usecase/               one type per use case, depends only on domain
      resolverule/ generaterule/ reportrule/ searchmetadata/ synclibrary/ releasefeed/
    adapter/
      http/                handlers, DTOs, mapping to use cases
      persistence/sqlite/  sqlc repositories implementing domain interfaces
      llm/                 provider implementing a domain port, structured output
      metadata/            AniList and MangaUpdates clients
  migrations/
```

- Endpoints: rule lookup by domain or fingerprint, rule candidate submission, rule reports, metadata search, sync push and pull, release feed.
- Auth: anonymous device token for free calls such as rule lookup, reports and release alerts. Account token for sync and AI detection. A premium account token raises the AI detection quota and unlocks translation. See Accounts and monetization.
- Sync: last-write-wins per field with `updated_at` and tombstones.
- Rule payloads are signed with Ed25519; the app verifies before use.
- AI generation quotas are per account, sized by tier. A request joining a generation already running for the same domain does not use quota. Each domain is generated once, then shared with everyone.

## Accounts and monetization

Freemium, decided by the owner. Every feature that runs on the device is free and works without an account. An account is needed only for cloud features and billing. Premium needs an account and a subscription.

| Tier | Needs | Features |
|---|---|---|
| Free, no account | Nothing | Library, tracking, browser, adblock, reader, downloads, new-chapter checks and notifications, using detection rules that already exist |
| Free, with account | A free account | Everything above, plus cloud sync and AI detection within a small quota |
| Premium | An account and a subscription | Everything above, a larger AI detection quota, AI translation, and anything else that needs substantial server processing |

AI detection means asking the server to generate a rule for a site that has none.

- It needs an account, free or premium. Quotas are per account, because per-device quotas reset on every reinstall.
- Starting quotas: 10 per month on a free account, 200 per month on premium, resetting on the 1st at 00:00 UTC. These are guesses, kept in server config, to be tuned from analytics after launch.
- It runs automatically when the lookup order reaches it and the user has quota left. No confirmation prompt.
- Without an account, or with no quota left, the app falls back to heuristics and the fix-detection sheet. Without an account it may also suggest signing in.
- AI translation is premium only.
- Generated rules are shared with everyone, so users without an account benefit once any signed-in user has visited a site.
- Never put a free feature behind sign-in, except sync and AI detection.
- The account also carries billing and the premium entitlement.
- Sign-in is OAuth, Google first. On Android use Credential Manager to get a Google ID token; the server verifies it and issues its own account token. Other providers come later, and email with a password is not planned.
- Brand gradient marks premium in the UI.

## Design

Everything visual is in `design/`. Read `design/tokens.md` before building any UI. Key rules:

- Neutral ink and gray surfaces, one accent `#D9472B` per screen, brand gradient only for progress and premium.
- Outer corner radius equals inner radius plus the actual gap. Get this exactly right; the owner checks it.
- Series headers use a cover-derived gradient with a screentone overlay, never the raw cover image.
- Floating ink bottom nav, glass bars only in the reader and over web content.
- Logo is Progress A, files in `design/brand/`.

## Build order

1. Scaffold: monorepo, Gradle and Go modules, CI with lint and tests, this file.
2. Contracts: OpenAPI spec, rule JSON Schema, conformance fixtures.
3. Go backend: rules and sync with tests.
4. Android shell: Compose theme from tokens, navigation, Home, Library, Series, Settings on fake data.
5. Browser core: WebView, injection bridge, navigation guard, rule evaluator.
6. Reader: extraction, native long-strip reader, progress, page-as-is fallback.
7. Adblock: adblock-rust via UniFFI, filter modules, protection sheet.

**Prototype first:** reader-mode extraction on hostile sites, and JS/Go evaluator parity. They are the highest risk and may change the architecture.

## Needed UI fixes

Found by the owner while testing steps 6 and 7. Decided with the owner: the UI pass comes next, before the rest of step 7. Screens are named by their file in `design/screens/`; behavior the artboards can't show is in `design/screens/notes.md`.

1. The browser's address bar is replaced by the Browser v2 bar: Back, an address pill with the shield count and reload, and Menu. Designed: BrowserV2Detected, BrowserV2Scrolling, notes.md.
2. The overflow menu is replaced by the Browser v2 menu. Designed: BrowserV2Menu, notes.md.
3. Returning to the reader from the site page: the reader chip above the bar. Designed: BrowserV2Docked, notes.md.
4. Leaving the browser in one tap: Close browser, the last row of the menu. Back on the first page of history also leaves. Designed: BrowserV2Menu, notes.md.
5. The reader lacks features for viewing comics. Designed: ReaderSettings, ReaderChapters, and notes.md for gestures, zoom, the long-press menu and the time left.
6. The detection card docks into the reader chip after a 5 second countdown. Designed: BrowserV2Detected, BrowserV2Docked, notes.md.
7. A caught-up series keeps its main button as a status block. Designed: SeriesCaughtUp, SeriesAhead, notes.md.
8. Glass bars in the reader and over web content have the glass color but no background blur.
9. The reader leaves out parts of the Reader screen: the Chapters and Translate buttons, the reader settings button, and the time left. The chapter end screens are not built. Designed: Reader, ReaderEnd, ReaderEndCaughtUp, ReaderEndComplete, notes.md.
10. The reader puts no gap between pages. Decided in notes.md: Auto by default, 0 px between images that look like slices of one strip and 6 px otherwise, with None or Small per series in reader settings.
11. The protection sheet is not built. Blocking works without it. Designed: BrowserShield, ShieldBroken. The shield chip in the address pill and the Protection menu row open it. notes.md sets what each switch does: app links and APK downloads are always blocked, a trusted site skips everything else, and "Block popups and redirects" off allows `window.open` as a same-tab navigation and redirects without a tap. Counts come from two engines, one for EasyList and the Ascon list as Ads and one for EasyPrivacy as Trackers; the guard counts stopped redirects. "Site looks broken?" trusts the site and reloads, with Undo.
12. The protection switches on the Settings screen change nothing yet, because settings are an in-memory fake. notes.md specifies one persistent protection settings repository in core, shared by Settings, the sheet and the browser, with pure, tested decision functions for requests and navigations, and the switch renamed to "Block popups and redirects".
13. The filter lists screen is not built. Designed: FilterLists, notes.md. It also lists trusted sites, and changes list updates to every 4 days on Wi-Fi, fetched with ETag.
14. There is no open source licenses screen. Designed: Licenses, notes.md: an About group at the bottom of Settings, libraries listed at build time by the AboutLibraries Gradle plugin, and EasyList and EasyPrivacy first with their CC BY-SA credit.

## Workflow

How agents work in this repo, agreed with the owner to keep the loop fast.

- **Spec as tests first.** Each step starts with a short list of acceptance criteria, each turned into a failing test before any UI. Logic lives in plain Kotlin, such as view models, state reducers and URL guards, and is tested with JUnit on the JVM.
- **Spike unknowns.** Before building on a platform API you have not used here, check its behavior with a 10-line experiment or one small test.
- **Compose through semantics.** While building a screen, test it with compose-ui-test under Robolectric and assert on the semantics tree. Record Roborazzi goldens once, at the end of the feature.
- **Warm, narrow Gradle.** Never pass `--no-daemon`; `gradle.properties` sets the memory and caches. In the inner loop, compile and test only the module you touched, for example `./gradlew :feature:browser:testDebugUnitTest --tests "*BrowserViewModelTest"`.
- **Gates once, before commit.** Run `ktlintFormat`, compile, then `./gradlew ktlintCheck detekt lint test -Proborazzi.test.verify=true`, the JS tests in `engine/detection` and the Rust checks in `engine/adblock/rust` if they changed, and `python3 tools/context.py`.
- **Inspect real sites from inside the app.** `android/tools/webview.py` runs JavaScript in the debug app's WebView, such as `tools/webview.py 'document.images.length'`. Use it to check a site's DOM as Ascon sees it before guessing from screenshots.
- **One device pass per feature.** Use Maestro flows in `android/maestro/flows`, not adb taps. Take screenshots only when a flow fails. See the README.
- **Protect context.** Read `android/CONTEXT.md` for the shared API before opening sources in `core` or `engine`. Keep new files under about 300 lines. Split an existing larger file only when a task already touches it.
- **Hold scope.** A step ships the minimum that passes its acceptance tests. Error pages, menus and extra rules become their own later steps.

## Conventions

- Small PRs, one milestone step each. Conventional commit messages.
- Every use case and every rule evaluator change has tests. Detection rules get golden tests against saved HTML fixtures.
- Fixtures saved from real sites go through `android/tools/fixture.py`, which strips scripts, shortens text and renames the site, so the repo never names one.
- Kotlin: ktlint and detekt. Go: golangci-lint. Compose screens get previews and screenshot tests.
- No new dependency without a one-line reason in the PR.
- Prose in docs and UI copy: plain, flat sentences. Avoid parentheses where a sentence works.

## Open questions

- LLM for rule generation: shortlist is `deepseek-v4-flash` or `gpt-5.6-luna`, not chosen. Before choosing, check the provider's API terms against the hard rule that inputs are not retained. Keep it behind the `llm` adapter so it can be swapped.
- Paywall pricing for translation: not decided.
- Privacy policy: deferred until more decisions are made. Not written yet. It must cover the push token and follow list the server stores for release alerts. See `docs/adr/0001-release-alerts-per-device-list.md`.
