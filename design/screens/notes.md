# Design notes

Behavior the artboards can't show, exported word for word from the canvas notes. Each note is the owner's decision.

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

## Protection, filter lists, licenses and reader tools

DECISIONS FOR THIS ROW (backlog 5, 10, 11, 13, 14)

PROTECTION COUNTS: use two adblock-rust engines, not debug rule info. Engine A holds EasyList + the Ascon list and counts as Ads. Engine B holds EasyPrivacy and counts as Trackers. Check A first, then B; a request blocked by A is not checked by B. Memory stays about the same because the lists are split, not duplicated. Debug rule info disables engine optimizations, so keep it for a dev build only. Redirects stopped is counted by the navigation guard, not the engine. All counts are per page load and reset on navigation.

SITE LOOKS BROKEN: expands an inline confirm in the protection sheet. Turn off and reload adds the site to trusted sites, reloads, closes the sheet, and shows a snackbar Protection off for <site> · Undo. Undo removes it and reloads. The optional Send site address switch is off by default and sends only the registrable domain to the backend so the Ascon list can be fixed. Never the URL path, never page content.

FILTER LISTS: reached from Settings > Filter lists and the protection sheet. Each list shows purpose, version and last update. Toggling calls Adblock.setEnabled and shows Applying changes in the status card until the rebuilt engine is swapped in. Update all fetches lists over HTTPS with ETag; automatic update every 4 days on Wi-Fi. Trusted sites are listed below with a remove button each.

LICENSES: Settings gets an ABOUT group at the bottom, below Ascon Plus, with Open source licenses, Privacy policy and the version number. Open source licenses opens this screen. Generate the library list at build time with the AboutLibraries Gradle plugin. Add EasyList and EasyPrivacy manually at the top with the CC BY-SA attribution card. Each row opens the full license text.

PAGE GAP (backlog 10): default Auto. In long strip, Auto puts 0 px between images when consecutive images share the same width and look like slices of one strip (height at least 1.6x width), otherwise 6 px. User can force None or Small (6 px) per series. Paged modes ignore the gap.

READER SETTINGS: Aa button. Scope switch at top: This series or All series; changes save per series unless All series is chosen. Mode: Long strip, Left to right, Right to left. Fit: Width or Screen. Page gap: Auto, None, Small. Background: Black, Gray, White. Switches: Crop borders, Keep screen on, Volume keys turn pages, Show tap zones.

READER GESTURES: tap center toggles controls. Paged: tap left or right third turns the page, mirrored in Right to left. Long strip: side taps scroll 80 percent of the screen. Double tap zooms 2x at the point, double tap again resets. Pinch zoom up to 4x. Long press an image: Save image, Share image, Report broken page. Pull past the last image to open the chapter end screen.

TIME LEFT: remaining images times this user's median seconds per image, learned across chapters. Hidden until 3 images are read in the session.

CHAPTERS SHEET: opened by the Chapters button. Source switcher at top, Go to chapter field, newest first by default, current chapter highlighted with its progress. New chapters get the accent dot, read chapters a check, downloaded ones a download mark.⁸
