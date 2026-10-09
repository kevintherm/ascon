# Ascon design

- `tokens.md` holds every color, type, spacing, radius and component value. Start here.
- `screens/` is an export of the "Ascon UI direction" canvas on claude.ai: one `.dc.html` file per artboard at 390×844, `canvas.json` for the layout, and `notes.md` for the behavior the artboards can't show. The files are canvas source: they render on the canvas, not opened alone, but their markup and inline styles are the reference for layout, sizes and hierarchy. They are not code to port line by line.
- `brand/` holds the chosen logo, Progress A, as SVG, plus a 1024px app icon.

## Screens

| File | Screen |
|---|---|
| Main | Home: cover-tinted Continue Reading header, status chips, new chapters, sites |
| Series | Series: merged sources, chapter list with read state |
| SeriesCaughtUp | Series with every chapter read: the main button becomes a status block |
| SeriesAhead | Series with a newer chapter only on another source |
| Browser | Browser with the detection card. Its bar is superseded by Browser v2 |
| BrowserV2Detected | Browser v2: just detected, card with a countdown |
| BrowserV2Docked | Browser v2: card docked into the reader chip above the bar |
| BrowserV2Scrolling | Browser v2: bar and chip collapsed while scrolling |
| BrowserV2Menu | Browser v2: the menu |
| Reader | Native reader with controls shown |
| ReaderEnd | End of chapter and up next |
| ReaderEndCaughtUp | End of the latest chapter |
| ReaderEndComplete | End of a completed series |
| Library | Library grid |
| Settings | Settings |
| BrowserTabs | Tab switcher. Dropped: the browser is single-tab |
| BrowserShield | Protection sheet |
| BrowserFallback | Fallback when reader extraction fails |
| SiteError | Network or ISP block, with secure DNS retry and alternate source |
| EmptyLibrary | Empty state |
| FixDetection | Correct a wrong title or chapter |
| Onboarding | First launch |
| Import | Import from AniList, Mihon, MangaPin, MangaUpdates |
| Logo, LogoAB | Logo exploration. Concept A is the pick |

Covers in the mockups are abstract gradients. Real covers replace them.

The editable canvas lives on claude.ai as "Ascon UI direction". If a screen changes there, export the whole canvas here again.

## Not designed yet

Reader settings sheet, chapter list sheet, translation overlay, downloads manager, filter list manager, open source licenses, hidden library lock, Plus paywall, dark theme for non-reader screens, motion.
