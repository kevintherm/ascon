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
- Strip the `X-Requested-With` header with `WebSettingsCompat.setRequestedWithHeaderOriginAllowList(emptySet())`. WebView 153 reports that switch unsupported and still sends `X-Requested-With: com.ascon.app` on every request: pages, files, fetch calls and frames. Removing it would mean sending requests through OkHttp, which cannot carry POST bodies and looks unlike Chrome to Cloudflare. Decided with the owner: keep the header.
- Navigation guard in `shouldOverrideUrlLoading`: block `intent://`, `market://` and other non-web schemes; block cross-domain navigations where `request.hasGesture()` is false; deny `onCreateWindow`; override `window.open`.
- Click hijacking: a tap counts for a cross-domain navigation only when the navigation goes to the link the user tapped. The injected script reports the link under each tap. A site's click handler that sends the page elsewhere is blocked like a redirect.
- Block APK and executable downloads in the download listener.
- Decided with the owner: the browser is single-tab, with one live WebView. Its toolbar is docked at the bottom, and showing or hiding it only moves the page's bottom edge. Opening a new site replaces the page, and the previous page stays in back history. Back to Ascon leaves the page loaded for the Browse tab; Close, or confirming Back on the first page, discards it.
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
4. Heuristics: JSON-LD, `og:title`, chapter patterns in the URL. A site whose chapter URLs carry an id takes the chapter from a JSON-LD `Chapter` naming this URL, by its name or else its position
5. AI generation, last resort, automatic for a signed-in user with quota left. Otherwise the user gets heuristics and the fix-detection sheet

**AI generation, server side:**

- Client sends sanitized snapshots: scripts, styles and form fields removed, only `class`, `id`, `href`, `src`, `data-src`, `data-lazy-src`, `srcset`, `data-srcset` and `rel` attributes kept, plus `name`, `property` and `content` on meta tags so rules can read `og:title`, text cut to 80 characters, size capped. Ideally two chapter pages from the same site.
- LLM returns a rule constrained to the JSON Schema.
- Server validates with a Go port of the evaluator: titles must match across samples, chapter numbers must parse and increase, enough images must be found, and a next or previous link the rule finds must match the chapter URL pattern in the same series, with a higher or lower chapter number from the URL's chapter group. Only then is the rule stored.
- The JS and Go evaluators share one conformance suite in `contracts/fixtures/` so they cannot drift.
- `singleflight` dedupes concurrent generation for the same domain.
- A domain the model wrote no usable rule for, after its attempts, is turned away for 7 days with an already rejected candidate, costing no tokens or quota. An answer that isn't a rule at all counts as a failed attempt, so the retry is used. Failures of the provider itself, such as timeouts, are not remembered.
- Built on 2026-10-10: the `llm` adapter speaks the OpenAI chat API in JSON mode, so DeepSeek and OpenAI both fit. Its prompt is `backend/internal/adapter/llm/prompt.txt`, whose example is the glasslight fixture rule. The model may offer two rules; the second is told why the first failed. A rule must also stay inside the selector and regex subset both evaluators share, checked by `rule.Portable`. Each sample is cut at 60 KiB. DeepSeek V4 Flash takes about 45 seconds per rule, so the app must wait for the candidate rather than hold the page.

**On the device**, decided while building the lookup on 2026-10-10:

- Backend answers are kept in Room for a day, a missing rule included, then refreshed in the background while the kept answer is used. A site seen for the first time waits up to 1.5 seconds for the backend, which usually answers while the page loads; a later answer is kept for the next page. An unreachable backend is asked again on the next page.
- A rule that fails its Ed25519 signature is treated as no rule. Debug builds pin the development key in `contracts/fixtures/signed-rule.json`, which the Go signer and the app's verifier both test against. Release builds have no backend address or key until the server is deployed.
- bridge.js sends a page's structure only from a chapter page read without the site's own rule, once per document: the generator meta tag, class names without digits, and parent>child tag pairs. The app hashes them into the fingerprint and asks the backend once per site. A borrowed rule is kept under the asking site with the fingerprint, so refreshing keeps it. A site where neither built-in rules nor heuristics find a chapter never sends its structure; AI generation covers it.
- Simhash is sensitive: one changed feature in 200 can flip a few bits, and the backend reuses a rule within 4. Tune the features or the limit with real mirror pairs once generated rules carry fingerprints.
- A Cloudflare check page, "Just a moment", is never read as a page of the site, even by the site's own rule. It is known by Cloudflare's `_cf_chl_opt` or `#challenge-form`.
- Health counts only the site's own rule from the backend. A page is judged once, by its best result, when the next page arrives, so the last page before the app is closed isn't counted. A WorkManager job sends the counts daily.
- The backend client registers the device on its first call and again if the token is refused. The token is in plain DataStore, since it is anonymous.
- AI detection, built on 2026-10-10: a chapter page only heuristics read, on a site the backend has no rule for and nothing to borrow by fingerprint, is asked for a snapshot. Two snapshots of different chapters go to the backend together, and the app polls until the rule is written, then keeps it like a looked-up rule and sends it to the page. Each site is tried once per run of the app, or again after the backend was unreachable; a used-up quota stops all sites until it resets. A page heuristics can't read as a chapter is never sent; the fix-detection sheet covers it. It runs while the user is signed in, with the account token from sign-in.

**Health:** successful extractions raise confidence; empty results or backward chapter jumps lower it. Below a threshold the rule is suspect and regenerated, keeping the previous version for rollback. Users are asked only when a rule is suspect, and several reports are needed before regeneration so one confused user cannot break a working rule.

**Fingerprint:** simhash of the generator meta tag, theme class names and DOM skeleton, so a new mirror domain inherits an existing rule.

### Series identity

- Tables: `series` with optional AniList and MangaUpdates ids, `source` linking a series to a site URL, `chapter` with a decimal number, `progress`.
- **Progress is keyed by series and chapter number, not by source.** Switching sources keeps the place.
- Matching: normalize the detected title, search AniList and MangaUpdates through the backend, fuzzy-match against all alternate titles, auto-link above a threshold, otherwise show the fix-detection sheet.
- Chapter parsing handles decimals like 10.5, volume prefixes, extras and split chapters.
- Decided with the owner: until the backend's metadata search exists, a detected chapter with a number adds its series to the library by itself, matched to existing series by exact title key only. The site becomes one of the series' sources. Debug builds seed the sample library into an empty database; release builds start empty.
- Each source keeps the chapter page last opened on it, and each chapter keeps the page it was last opened at and that page's source. The library opens a chapter at its own page when that is on the current source, else by swapping the number in a source's last address, preferring the current source, then any source known to have the chapter, and last at its own page on another source. A site whose addresses carry an id, not the number, can only open chapters the user has opened there. The main button, Reread, chapter rows and Home's Resume open the chapter in the browser, which loads it even if it is already shown, and the reader opens by itself at the saved page. When the chapter stays on the site, because the reader can't take it or is turned off there, the app sends the page a scroll message and bridge.js puts the saved place back in the middle of the screen, once per page the user asks for. The page on screen is the slot under the middle of the screen, measured on the screen so the toolbar doesn't move it. Progress keeps that page and how far down it the middle was, so a long strip slice reopens where the user was in it. The page holds that place while images above it load, for 5 seconds or until the user touches it. The reader saves whole pages for now. A chapter no source has an address for doesn't open yet.
- On the device the library lives in Room, in `core/data/room`. Rules for how reading changes a series are plain functions in `core/data/LibraryChanges.kt`, shared by Room and the fake. Schemas are exported to `core/schemas`; every schema change after version 1 needs a migration and a test against the previous schema.

### Reader mode

- Decided with the owner: the reader opens by itself when a chapter's pages are found. Back returns to the site page, and a page the reader already showed stays on the site. A switch in the reader settings, Open reader automatically on <site>, turns this off for the whole site and is saved with DataStore; only that switch turns it back on. Until the user sets that switch for a site, a chapter where detection finds no next or previous chapter stays on the site, since the reader would have no way on and the site's own buttons do; the switch shows off there, and turning it on opens the reader by itself on that site from then on. With it off, chapters stay on the site and the menu's Open in Reader or the detection card still opens the reader.
- Decided with the owner: the browser toolbar has no Reader button. A chapter page shows the tracked page chip, p. N, once the page on screen is known, whether or not the reader can take the chapter.
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
- Built on 2026-10-10: the app asks Credential Manager's Sign in with Google option for an ID token issued to the OAuth Web client, `ascon.googleWebClientId` in the untracked `local.properties`. `POST /v1/sessions` checks it against Google's published keys, its issuer, its audience `ASCON_GOOGLE_CLIENT_ID` and its expiry, and finds or creates the account by Google's user id. The server stores only that id; the name and email stay on the phone. Each sign-in gets its own account token, kept in app-private DataStore with backups off. Signing out forgets the account on the phone first, so it works offline, then ends the token with `DELETE /v1/sessions/current`. A token the server refuses when the account sheet loads the quota signs the phone out. Debug builds need the debug signing certificate's SHA-1 on the Android OAuth client, and release builds will need theirs.
- Settings follows SettingsSignedOut, SettingsSigningIn, SettingsSignInFailed, AccountSheet, AccountSheetFree and AccountSignOut. Not built yet: the sheet's Upgrade button, AI translation row and Last synced row, which wait for billing, translation and sync; and FixDetectionSignedOut and FixDetectionNoQuota, which wait for the fix-detection sheet. Until sync exists, the Settings header shows the account's email in place of the sync time. Before release, the Sign in with Google button needs Google's own G logo, as Google's branding guidelines ask.
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

1. Done: the Browser v2 bar. Redesigned by the owner: see item 15.
2. Done: the Browser v2 menu sheet. Find, Private, Desktop site and Fix title or chapter wait for their screens and settings.
3. Done: the reader chip, which opens the reader at the page on screen.
4. Done: Back to Ascon in the menu, and the Browse nav item returns to the loaded page.
5. Done: the reader settings sheet from Aa, per ReaderSettings, saved with DataStore for the series or for all series, with every setting working. Paged modes show one page per screen, turned by swiping or the side thirds, mirrored in Right to left; Fit width lets a tall page scroll. Crop borders trims plain margins as a page decodes: the sides in long strip, so strip slices keep joining, and all four edges in paged modes. Show tap zones shades and names the thirds while the bars show. The Chapters sheet, per ReaderChapters, lists the series' chapters and opens one by swapping the number in this chapter's address; the source switcher is not built yet, though sources now keep a chapter address to build it on. In long strip, side thirds scroll 80 percent of the screen at once and the middle third toggles the bars. Decided while building: only a double tap in the middle zooms, 2x at the point, so quick side taps each scroll. Pinch zooms up to 4x; long strip lays the pages out wider so the list still scrolls to its end. A long press on a page offers Save image, from Android 10, and Share image; Report broken page waits for the backend's rule reports. The time left in the panel is the images left times this user's median seconds per image, from the latest 200 images read at a pace, shown after 3 images in the chapter. Designed: ReaderSettings, ReaderChapters, and notes.md.
6. Done: the card counts down and docks into the reader chip.
7. Partly done: a series with every chapter read shows the Caught up status block, Ch. N is latest, in place of its main button. When the current source has nothing more but another source has the next chapter, the button reads Read Ch. N, Only on <source> so far. The main button, Reread and the chapter rows open their chapter. Still to build: Finished, which needs a completed status on series; and the note line while alerts are on. Designed: SeriesCaughtUp, SeriesAhead, notes.md.
8. The reader's top bar and panel have the glass color but no background blur, per Reader. The redesigned browser screens use no blur, so only the reader needs it.
9. Partly done: the chapter end after the last page, with Up next and Read Chapter N, or You're caught up, and Back to series. The reader panel's Chapters and Aa buttons and the time left came with item 5; its Translate button waits for translation. Still to build: ReaderEndComplete, which needs a completed status on series; and the end buttons for features not built yet, such as Notify me, Mark as caught up, Find on other sites and Rate on AniList. Designed: Reader, ReaderEnd, ReaderEndCaughtUp, ReaderEndComplete, notes.md.
10. Done: the Auto page gap, 0 px between slices of one strip and 6 px otherwise. A pair joins when the widths match and either image is at least 1.6 times as tall as wide, so a strip's short last slice joins too. None or Small is chosen per series or for all series in the reader settings.
11. Done: the protection sheet, from the shield chip and the menu's Protection row, per BrowserShield and ShieldBroken. One engine counts ads and one trackers, and the guard counts redirects stopped. Changes from the sheet reload the page. Not built: the Send the site address switch, which needs a backend endpoint, and the Filter lists button, which waits for item 13.
12. Done: protection settings are saved with DataStore in `core/data/datastore` and shared by Settings and the browser. `NavigationGuard` applies them to navigations, requests and hidden elements, and the filter lists the user turns off are a protection setting.
13. The filter lists screen is not built. Designed: FilterLists, notes.md. It also lists trusted sites, and changes list updates to every 4 days on Wi-Fi, fetched with ETag.
14. There is no open source licenses screen. Designed: Licenses, notes.md: an About group at the bottom of Settings, libraries listed at build time by the AboutLibraries Gradle plugin, and EasyList and EasyPrivacy first with their CC BY-SA credit.
15. Done: the browser toolbar is docked at the bottom, decided with the owner after trying the top, per BrowserV2Detected, BrowserV2Docked, BrowserV2Scrolling, BrowserV2Fallback and notes.md. It replaces the floating bar, the reader chip and the collapsed strip. The detection card is the only overlay. The toolbar's Reader button was later replaced by the tracked page chip, as Reader mode says.
16. Done: the menu ends with Back to Ascon, with the app icon, which keeps the page, and Close, which discards the page and its history and shows Browser closed · Undo. Decided with the owner: Back on the first page of history asks before it closes the browser, instead of leaving it.
17. Done: every bottom sheet can be dragged down to close. A sheet taller than the screen scrolls under its grabber, and pulling down at the top of its scroll drags it.

## Known bugs

Found by the owner, to fix later.

1. On asurascans.com the reader shows blurry pages. The site's pages are single 800 by about 11,600 px strips, and Coil decodes at most 4096 by 4096 px by default, so a page is decoded at about 282 by 4096 and stretched about 3.8 times to the screen's width. Raising the limit would mean bitmaps of about 67 MB per page. Fix with tiled decoding for tall strips, per the stack table, or by splitting tall pages into pieces as they decode. Chapter 150 of Trash of the Count's Family is free to test with; newer chapters ask to sign in.

## Deferred until after the MVP launch

Decided with the owner on 2026-10-10: the big features come first, and these smaller items wait until after the first MVP launch. Do not drop them.

- Known bug 1, blurry Asura pages, fixed with tiled decoding or by splitting tall pages.
- UI fix 8, the reader's background blur.
- UI fix 13, the filter lists screen, with trusted sites and list updates every 4 days with ETag.
- UI fix 14, the open source licenses screen. EasyList and EasyPrivacy must be credited under CC BY-SA, so check before launch whether a minimal credit is needed sooner.
- The rest of UI fix 7: Finished, and the note line while alerts are on.
- The rest of UI fix 9: ReaderEndComplete and the chapter end buttons for features not built yet.
- The reader saving how far down a page the user is, as the site already does, so moving between the reader and the site lands exactly.
- Keeping the browser toolbar on screen after a resume scroll.
- The protection sheet's Send the site address switch, and Report broken page in the reader, once the backend endpoints exist.
- The source switcher in the reader's Chapters sheet.

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

- LLM for rule generation: shortlist is `deepseek-v4-flash` or `gpt-6-luna`, not chosen. A development comparison on 2026-10-10 is only a data point: on the same pages gpt-6-luna answered in about 10 seconds with about 1,200 output tokens, deepseek-v4-flash in 28 to 48 seconds with 4,000 to 8,000, nearly all reasoning, and both rules passed. Development uses `deepseek-v4-flash` through the owner's OpenAI-compatible proxy, set in the untracked `backend/.env`; that proxy is for development only. Before choosing, check the provider's API terms against the hard rule that inputs are not retained. Keep it behind the `llm` adapter so it can be swapped.
- Paywall pricing for translation: not decided.
- Privacy policy: deferred until more decisions are made. Not written yet. It must cover the push token and follow list the server stores for release alerts. See `docs/adr/0001-release-alerts-per-device-list.md`.
