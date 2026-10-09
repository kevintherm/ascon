# Design notes

Behavior the artboards can't show, exported word for word from the canvas notes. Each note is the owner's decision.

## Browser v2

BROWSER V2 BEHAVIOR (supersedes every earlier browser bar, including Browser · detected and Browser · reader unavailable)

RULE: nothing stays floating over the page while reading. The toolbar is docked at the TOP, like Chrome's. The WebView starts below it, so the site's own top and bottom bars stay reachable. Back sits top-left, the same place as the back button on every other Ascon screen.

TOOLBAR: full width, ink, flat bottom edge, 64 tall below the status bar, padding 8 12. Left to right, gap 4: Back (bare icon, 40 wide), address box (shield count + domain, 44 tall, radius 12; shield chip 26 tall, radius 6; tap to edit the URL), Reader button (only on a detected chapter page: accent, 44 tall, radius 12, icon + Reader), Menu (bare icon, 40 wide). Address box and Reader button share height and radius on purpose. Reload lives in the menu. In page-as-is mode the Reader button becomes the neutral p. N tracking chip. No close or exit button in the toolbar, to avoid accidental taps.

MENU FOOTER, two buttons side by side, 56 tall, radius 16:
Back to Ascon (ink, app icon, wider): leaves the browser but keeps the page loaded, so the Browse tab returns to it exactly.
Close (light, X): ends the browsing session. Returns to the screen that opened the browser, discards the page and its Back history, and shows a snackbar there: Browser closed · Undo. Undo reopens the same URL at the same scroll position within 5 s. Reading progress is already saved, so closing never loses progress.
While a page loads, a 2 px accent progress line runs along the toolbar's bottom edge.

SCROLL: scrolling down more than 24 px slides the toolbar up out of view; when the slide ends, the WebView grows to full height, once. Scrolling up, or reaching the top or end of the page, slides it back and the WebView shrinks back. Never resize mid-animation. While hidden, the only thing drawn is a 2 px reading progress line along the top edge, in the brand gradient, on detected chapter pages only. It ignores touches (pointer-events none) and tracks the visible image index, not raw scroll position.

DETECTION CARD: the only overlay, and it is temporary. On a new detection a white card slides up at the BOTTOM (left/right 12, bottom 16 above the gesture area) with Open in Reader and Not right?, plus a 5 s countdown line. When it runs out, the card slides down and the Reader button in the toolbar pulses once to show where it went. Touch pauses the countdown; swipe down or the chevron dismisses early. 8 s and Add to library when the series is not in the library. Shows again only when the chapter changes.

RETURN TO READER: the toolbar's Reader button, visible whenever the page is a detected chapter page. Opens the reader at the currently visible image. Reader back returns to this page and scroll position.

PAGE AS-IS: the Reader button becomes a neutral Tracking chip with the page number. On the first load only, a dismissable notice row sits under the toolbar, inside the docked area, pushing the page down. Never a banner over the page.

Back: walks page history; on the first page it leaves the browser. Predictive back.
Single tab only.

MENU (bottom sheet): Forward, Reload, Share, Find · Open in Reader, Go to series, Fix title or chapter · Protection, Desktop site, Private browsing, Open in another browser · Back to Ascon and Close.

## Series main button and chapter end

SERIES MAIN BUTTON: never disappears, never changes height. First match wins:
1. Nothing read: accent Start reading
2. Chapter in progress: accent Continue Ch. N, page subtext
3. Unread chapter on this source: accent Read Ch. N+1
4. All read here, another source is ahead: accent Read Ch. N+1, subtext Only on <source> so far; opens that source for that chapter only
5. All read, ongoing: white status block, check, Caught up, subtext Ch. N is latest, icon-only Reread button (rotate icon, label Reread from Ch. 1). Block 54 tall, radius 27, padding 8, inner 38 radius 19. Note line under it while alerts are on
6. All read, completed: same block, Finished, subtext All N read
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
