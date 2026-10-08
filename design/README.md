# Ascon design

- `tokens.md` holds every color, type, spacing, radius and component value. Start here.
- `screens/` holds the approved mockups as plain HTML at 390×844. Open them in a browser. They are references for layout and hierarchy, not code to port line by line.
- `brand/` holds the chosen logo, Progress A, as SVG, plus a 1024px app icon.

## Screens

| File | Screen |
|---|---|
| 01-home | Home: cover-tinted Continue Reading header, status chips, new chapters, sites |
| 02-series | Series: merged sources, chapter list with read state |
| 03-browser-detected | Browser with the detection card |
| 04-reader | Native reader with controls shown |
| 05-reader-chapter-end | End of chapter and up-next |
| 06-library | Library grid |
| 07-settings | Settings |
| 08-browser-tabs | Tab switcher with private mode |
| 09-browser-protection-sheet | Per-site adblock sheet |
| 10-browser-reader-unavailable | Fallback when reader extraction fails |
| 11-error-site-unreachable | Network or ISP block, with secure DNS retry and alternate source |
| 12-empty-library | Empty state |
| 13-fix-detection-sheet | Correct a wrong title or chapter |
| 14-onboarding-welcome | First launch |
| 15-onboarding-import | Import from AniList, Mihon, MangaPin, MangaUpdates |
| brand-logo-concepts | Logo exploration. Concept A is the pick |

Covers in the mockups are abstract gradients. Real covers replace them.

The editable canvas lives on claude.ai as "Ascon UI direction". If a screen changes there, re-export it here.

## Not designed yet

Reader settings sheet, chapter list sheet, translation overlay, downloads manager, filter list manager, hidden library lock, Plus paywall, dark theme for non-reader screens, motion.
