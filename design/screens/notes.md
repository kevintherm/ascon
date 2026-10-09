# Design notes

Behavior the artboards can't show, exported from the canvas notes. Each note is the owner's decision.

## Browser v2

BROWSER V2 BEHAVIOR (supersedes the bar on Browser · detected)

Bar: Back · address pill (shield count, domain, reload) · Menu. No exit button in the bar, to avoid accidental taps. Ink pill, left/right 12, bottom 18, 68 tall, radius 34, padding 10, items 48 radius 24.

Back: walks page history. On the first page of history it leaves the browser. Use predictive back.
Close browser: last row of the menu. Leaves the browser in one tap, back to the screen that opened it. The page stays loaded and the Browse nav item returns to it.

Single tab only. No tab switcher. Opening a new site replaces the page; the previous page stays in Back history.

Detection card: 5 s countdown line, then it docks into the full-width reader chip above the bar (8 s and an Add button if the series isn't in the library). Touch pauses the countdown, chevron docks now, scrolling one screen docks it. Shows again only when the chapter changes.

Reader chip: visible while on a detected chapter page. Reader opens that chapter at the current visible image. Reader back returns to this page and scroll position.

Scrolling down >24 px: bar and chip collapse to a 36-tall glass strip with the same left/right edges. Only height animates, 200 ms. Scroll up, tap, or page end expands it.

Menu: Forward, Share, Find, Private toggle · Open in Reader, Go to series, Fix title or chapter (only when detected) · Protection (opens protection sheet), Desktop site, Open in another browser · Close browser, ink row at the bottom.
Shield chip and Protection row both open the protection sheet.

## Series main button and chapter end

SERIES MAIN BUTTON: never disappears, never changes height. First match wins:
1. Nothing read: accent Start reading
2. Chapter in progress: accent Continue Ch. N, page subtext
3. Unread chapter on this source: accent Read Ch. N+1
4. All read here, another source is ahead: accent Read Ch. N+1, subtext Only on <source> so far; opens that source for that chapter only
5. All read, ongoing: white status block, check, You're caught up, Ch. N is the latest, Reread chip (54 tall, radius 27, padding 8, inner 38 radius 19). Note line under it while alerts are on
6. All read, completed: same block, Finished, All N chapters read
States 5 and 6 have no accent on screen.

CHAPTER END CARD: next on this source, Read Chapter N+1. Next only elsewhere, Read on <source>. Caught up and alerts off, Notify me about Ch. N+1. Caught up and alerts on, no primary, status line. Final chapter of a completed series, gradient bar and Move to Done. No next chapter means no Keep scrolling hint.

Home header never shows caught-up or finished series.

## Settings persistence

SETTINGS PERSISTENCE (backlog 12)

One DataStore-backed ProtectionSettingsRepository in core, exposed as a StateFlow, shared by Settings, the protection sheet and the browser. Delete the in-memory fake.
Keys: adblock_enabled (true), block_popups (true), disabled_filter_lists (empty set), trusted_sites (empty set, registrable domains), secure_dns (cloudflare).

Browser reads settings.value synchronously in shouldInterceptRequest and shouldOverrideUrlLoading. No suspend, no runBlocking. Put the logic in pure decideRequest and decideNavigation functions with unit tests.
Order: 1 intent://, market:// and APK downloads are always blocked. 2 Trusted site skips everything else. 3 Adblock off skips engine and cosmetics. 4 Popups off allows window.open as same-tab navigation and gestureless redirects.
Network rules apply on the next request. Cosmetics need a reload: auto-reload when toggled from the protection sheet, next load when toggled from Settings.
Filter list toggles rebuild the engine in the background; keep the old engine until the new one is ready.
Rename the switch to Block popups and redirects, subtitle App links and APK downloads are always blocked.
