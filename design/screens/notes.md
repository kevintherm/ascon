# Design notes

Behavior the artboards can't show, exported word for word from the canvas notes. Each note is the owner's decision.

## Browser v2

BROWSER V2 BEHAVIOR (supersedes every earlier browser bar, including Browser · detected and Browser · reader unavailable)

RULE: nothing stays floating over the page while reading. The toolbar is docked at the BOTTOM, in thumb reach. The WebView ends above it, so the site's own top and bottom bars stay reachable, and showing or hiding the toolbar only moves the WebView's bottom edge: content at the top never jumps.

TOOLBAR: full width, ink, flat top edge, 72 tall above the gesture area, padding 8 12 20. Left to right, gap 4: Back (bare icon, 40 wide), address box (shield count + domain, 44 tall, radius 12; shield chip 26 tall, radius 6; tap to edit the URL), Reader button (only on a detected chapter page: accent, 44 tall, radius 12, icon + Reader), Menu (bare icon, 40 wide). Address box and Reader button share height and radius on purpose. Reload lives in the menu. In page-as-is mode the Reader button becomes the neutral p. N tracking chip. No close or exit button in the toolbar, to avoid accidental taps.

MENU FOOTER, two buttons side by side, 56 tall, radius 16:
Back to Ascon (ink, app icon, wider): leaves the browser but keeps the page loaded, so the Browse tab returns to it exactly.
Close (light, X): ends the browsing session. Returns to the screen that opened the browser, discards the page and its Back history, and shows a snackbar there: Browser closed · Undo. Undo reopens the same URL at the same scroll position within 5 s. Reading progress is already saved, so closing never loses progress.
While a page loads, a 2 px accent progress line runs along the toolbar's top edge.

SCROLL: scrolling down more than 24 px slides the toolbar down out of view; when the slide ends, the WebView grows to full height, once. Scrolling up, or reaching the top or end of the page, slides it back and the WebView shrinks back. Never resize mid-animation. While hidden, the only thing drawn is a 2 px reading progress line along the top edge, in the brand gradient, on detected chapter pages only. It ignores touches (pointer-events none) and tracks the visible image index, not raw scroll position.

DETECTION CARD: the only overlay, and it is temporary. On a new detection a white card slides up just above the toolbar (left/right 12, 12 above the toolbar) with Open in Reader and Not right?, plus a 5 s countdown line. When it runs out, the card slides down and the Reader button in the toolbar pulses once to show where it went. Touch pauses the countdown; swipe down or the chevron dismisses early. 8 s and Add to library when the series is not in the library. Shows again only when the chapter changes.

RETURN TO READER: the toolbar's Reader button, visible whenever the page is a detected chapter page. Opens the reader at the currently visible image. Reader back returns to this page and scroll position.

PAGE AS-IS: the Reader button becomes a neutral Tracking chip with the page number. On the first load only, a dismissable notice row sits above the toolbar, inside the docked area, shortening the page from the bottom. Never a banner over the page.

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

## Account

ACCOUNT BEHAVIOR

SIGNED OUT is a normal state, not an error. Library, progress and protection work fully on the phone. An account adds two things: sync across devices, and AI detection for sites Ascon has no rule for.

SETTINGS HEADER: signed out shows a generic person icon, Not signed in, and the Sign in with Google button with one line on what an account unlocks. Signed in, the whole header row opens the Account sheet.
Signing in: the button disables, shows a spinner and Signing in. Other settings stay usable.
Failed: an inline message above the button, and the button reads Try again. Cancelling the Google picker is not a failure: return to the default state silently.

HOME AVATAR: signed out, a generic person icon; tap goes to Settings with the sign-in row. Signed in, the initial (or Google photo); tap opens the Account sheet.

ACCOUNT SHEET: avatar, name, email. Plan row: Free (neutral chip + Upgrade) or Premium (brand gradient chip; the only place the gradient marks a tier). AI detections left this month, N of M · resets <date> (server config, starting at 10 Free and 200 Premium, reset the 1st at 00:00 UTC), meter in ink, turns accent at 0. AI translation row: Premium shows Included, no quota; Free shows a Premium lock and notes that on-device translation stays free. Last synced, with Sync now. Sign out opens a confirm dialog; the library stays on the phone.

FIX DETECTION SHEET, same slot as the Teach Ascon switch, first match wins:
1 Signed out and the site has no rule: Sign in to let Ascon learn this site.
2 Signed in, quota 0: No AI detections left this month · resets <date>. Manual fix still saves.
3 Otherwise: the Teach Ascon this site switch.

## Series matching

SERIES MATCHING (Next steps 3)

SHEET: opens from Not right? and Confirm on the detection card, the menu's Fix title or chapter, and Change match on a series. The field starts filled with the detected title and the list shows that search's candidates. Typing searches again 400 ms after the last key, through the backend's metadata search. AniList and MangaUpdates results share one list; each row names its source, type and year. A result already in the library says In your library.

LIST STATES, first match wins:
1 Searching: three placeholder rows and a spinner in the field. The field stays editable; a new key cancels the old search.
2 Couldn't search, offline or failed: error note with Try again.
3 No results: says nothing matched and preselects None of these.
4 Results.
None of these is the last row in every state, so a failed or empty search never blocks saving.

NONE OF THESE: selecting it adds a Name in your library field, filled with the detected title. Save reads Add to library and adds the series with no AniList or MangaUpdates id. It can be linked later from the series page.

SAVE: disabled until a row is selected. The Chapter stepper and the account slot from Fix detection sheet stay below the list. The footer stays pinned while the list scrolls under it.

AUTO LINK: a match above the threshold still links without the sheet. Below it, nothing joins the library until the user confirms.

## Unconfirmed titles, changing the match, merging

UNCONFIRMED TITLE: a chapter was detected but its title matched nothing confidently. The card's eyebrow reads Not in your library yet, with a hollow dot, and Chapter N · not saved yet. Confirm series opens the matching sheet. Open in Reader still works. Reading is kept on the phone as pending, not synced, and moves into the series when it is confirmed; Not a comic drops it. The card counts down 8 s; after it docks, the toolbar's tracking chip carries an accent dot until the series is confirmed, and tapping the chip brings the card back.

SERIES MATCH SHEET: opens from the Linked to AniList chip and from More > Series match. Shows the linked entry, View on AniList, Change match, which opens the matching sheet with the series' title, and Unlink, which keeps it as its own series and shows Unlinked · Undo. Neither touches progress, read marks or sources.

NOT LINKED: the chip reads Not linked, outlined, and opens the same sheet with Find a match in place of the linked entry. Without a cover from AniList, the header uses the fallback tint and the cover tile shows the title's initials. A Link this series row sits under the main button until linked or dismissed. Server release alerts need a link; device checks of the sources still run.

MERGE: when the chosen match is already in the library as another entry, Save reads Merge and save. The two become one entry: sources joined, read marks joined, and the place is the chapter read most recently, as Series identity says. The linked series keeps its title; the other title becomes an alternate title so its site matches next time. Snackbar Merged into <title> · Undo for 8 s, longer than other snackbars because it is the most expensive to redo. Undo restores both entries exactly, so keep the absorbed rows until the snackbar ends. Open for the agent: how the absorbed series' sync id is retired, since phones do not send tombstones yet.

## Hand-editing read marks

HAND-EDITING READ MARKS (Next steps 4)

WHERE: long press a chapter row opens the chapter sheet; its last row, Select chapters, starts select mode for several at once. No swipe actions: a swipe fights scrolling, the back gesture and sheet drag, which is the MangaX failure. A tap on a row still opens the chapter.

CHAPTER SHEET, actions by the row's state:
Read: Mark as unread, Mark read up to here, Remove from history.
Unread or new: Mark as read, Mark read up to here.
In progress: Mark as read, Mark as unread, Mark read up to here, Remove from history. Mark as unread here also clears the page.
Mark read up to here marks this chapter and every earlier chapter the phone knows; its subtitle names the range.

SELECT MODE: the cover header gives way to a plain bar with Close, N selected and Select all. Rows show a check circle and tapping toggles one. A floating ink action bar, the bottom nav's shape, offers Mark read, Mark unread and Remove. Back or Close leaves select mode.

REMOVE FROM HISTORY: forgets the read mark, the page and the source the chapter was last opened on. A chapter a series page check still knows stays as a plain row: no marker, Not read in the meta. A chapter only history knew disappears. If it was the place, the place moves to the most recently read chapter left, else the main button shows Start reading.

PLACE: marks never move the place, except Mark as read on the chapter in progress, which moves it to the next chapter, and Remove on the place, above.

UNDO: every edit shows a snackbar: Marked 3 chapters read · Undo, Removed Chapter 11 from history · Undo. 5 s, swipe or tap outside to close early, per Improvements 1. Edits sync like reading does.

SNACKBAR: ink, 52 tall, radius 18, 12 from the sides, 24 above the bottom or the nav. Undo is 36 tall, inset 8, radius 10.

## Search, Browse and the series menu

ONE SEARCH SCREEN: Home's field, Library's search button and Browse's field all open it, focused, with the keyboard up. Back returns to the screen that opened it. From Library it starts with the Library only chip on, and turning it off searches everything.

IDLE: Recent, the last 8 queries and addresses, each removable, with Clear. Recents stay on the phone and are never synced. Your sites below.

TYPING, sections in this order, each hidden when empty:
1 Go to <address>, only when the text looks like an address: it has a dot and no spaces, or a scheme. Enter goes there.
2 In your library: series whose title or any alternate title matches, best match first, with the place and a Continue button that opens the chapter, as Resume does. A tap on the row opens the series.
3 You read here, only for an address: library series with a source on that domain.
4 On AniList: series not in the library, from the backend's metadata search, 400 ms after the last key. Plan adds one to the library with status Plan and no source; it gets a source the first time a chapter of it is detected. Offline, this section is a single line, Couldn't search AniList, with Try again.
5 Search the web for <text>, last, in the browser with the default search engine.
Library matches are local and instant, so they never wait for AniList.

BROWSE IDLE: when Back to Ascon or Keep browsing left a page loaded, Open page sits at the top: site, series and chapter when detected, else the page title, the page on screen, and Return to page. This fixes Known bug 2. Its X closes the page, the same as the menu's Close, with Browser closed · Undo. With no page kept, the card is gone. Your sites come next, Edit opens the reorder and remove list, then Recently visited, the last 5 pages with a known chapter, each reopening its address. A first launch with no sites shows the onboarding site picks instead.

SERIES MORE SHEET, from the series header's three dots: Status, a segmented control that saves at once; Series match, opening the match sheet; Reader settings, opening the reader settings sheet scoped to This series; Select chapters; Share, which shares the title and its AniList link, never a site address; and Remove from library in danger red, which confirms and offers Undo. Remove waits for sync tombstones from the phone, so build it with them.
