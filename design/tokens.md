# Ascon design tokens

Direction name: **Ink & Screentone**. Modern Material structure, a touch of glass, precisely nested corners, one accent. Neutrals do the work. Color is reserved for action, progress and premium.

The source of truth for layout is `design/screens/*.html`. This file is the source of truth for values. When they disagree, this file wins and the screen gets fixed.

## Color

### Light surfaces

| Token | Hex | Use |
|---|---|---|
| `ground` | `#F3F4F6` | App background |
| `surface` | `#FFFFFF` | Cards, sheets, rows |
| `surfaceMuted` | `#E6E8EC` | Unselected chips, tile fills, segmented track |
| `surfaceSunken` | `#ECEEF1` | Media stage behind icons, progress track, row dividers |
| `border` | `#D5D8DE` | Outline buttons, off-state switch track |
| `borderDashed` | `#C9CDD4` | "Add" tiles, empty radio |

### Ink and text

| Token | Hex | Use |
|---|---|---|
| `ink` | `#15161A` | Primary text, selected chips, nav bar, primary dark buttons, on-switch track |
| `ink2` | `#26282E` | Address field inside the dark nav bar |
| `textMuted` | `#5B5F69` | Secondary text. Passes 4.5:1 on white and ground |
| `textSubtle` | `#8A8E97` | Chevrons and read-state icons only, never body text |

### Dark surfaces, reader and browser

| Token | Value | Use |
|---|---|---|
| `readerGround` | `#0E0F12` | Reader background |
| `darkCard` | `#1B1C21` | Cards on reader ground |
| `browserGround` | `#1B1C20` | Behind web content |
| `glass` | `rgba(20,21,26,0.78)` + 20px backdrop blur + 1px `rgba(255,255,255,0.08)` border | Reader bars, floating browser banners |
| `onDarkMuted` | `rgba(255,255,255,0.72)` | Secondary text on dark |
| `onDarkFill` | `rgba(255,255,255,0.08)` | Secondary buttons on dark |

### Accent

| Token | Hex | Use |
|---|---|---|
| `accent` | `#D9472B` | Primary action per screen, new-chapter dots, badge fills |
| `accent2` | `#F5A54A` | Only as the start of the brand gradient |
| `success` | `#2E9E5B` | Sync status dot only |
| `danger` | `#B42318` | Sign out and other actions that end something |
| `danger-soft` | `#FCEDEA` | Ground of an error note, such as Couldn't sign in |
| `danger-strong` | `#8F1D12` | Title of an error note |
| `danger-body` | `#5B2A24` | Body of an error note |

**Brand gradient** is `accent2 → accent`, left to right. Use it only where something fills up or costs money: reading progress bars, the page scrubber, import progress, the Plus upsell. Never on backgrounds or large areas.

**One accent action per screen.** If a screen has two candidates, the second one is ink.

### Cover tint header

Series-driven headers never show the cover as-is. Recipe:

1. Extract two colors from the cover with the AndroidX Palette API. Prefer dark muted and dark vibrant swatches. Fallbacks: `#7A1E2C` and `#24132F`.
2. Linear gradient at 155–160°, color A to color B.
3. A blurred glow of the cover's dominant color: about 260×300, 40px corners, 42–48px blur, 50–55% opacity, partially off-screen.
4. Screentone overlay: white dots, 1.1px radius on a 7px grid at 18–20% opacity, masked by a linear gradient so it fades out across the header.
5. Text on top is always white. Check contrast against color A; darken A if it fails 4.5:1.

## Type

Family: **Plus Jakarta Sans**, weights 400–800. Bundle it as a downloadable or packaged font. Do not fall back to Roboto in production.

| Role | Size / line height | Weight | Tracking |
|---|---|---|---|
| Display, onboarding | 38 / 1.05 | 800 | -0.035em |
| Screen title | 30 / 1.1 | 800 | -0.03em |
| Header title | 23–25 / 1.15 | 800 | -0.01em |
| Section title | 18 | 800 | -0.01em |
| Row title | 15 | 700 | 0 |
| Button | 15–16 | 700 primary, 600 secondary | 0 |
| Body | 15 / 1.5 | 400 | 0 |
| Meta | 13 | 400–600 | 0 |
| Caption | 12 | 500–600 | 0 |
| Eyebrow | 12–13 caps | 700 | 0.1–0.12em |

## Spacing

4-based scale: `4 6 8 10 12 14 16 18 20 24 28 32 48`. Screen side gutter is 16. Gaps between sections are 14–18. Touch targets are at least 44×44.

## Corner radius

### The rule

**Outer radius = inner radius + gap.** The gap is the actual distance from the inner element's edge to the container's edge on that corner, not the CSS padding value. When a child is centered vertically, compute the gap from the real geometry.

Worked examples from the screens:

| Container | Outer | Gap | Inner |
|---|---|---|---|
| Bottom nav, 68 tall, 48-tall items | 28 | 10 | 18 |
| Browser bar, 68 tall, 48-tall items, 10 side padding | 34 | 10 | 24 |
| Detection card, padding 14 | 28 | 14 | 14 |
| Chapter-end card, padding 16 | 28 | 16 | 12 |
| Reader bottom panel, padding 14 | 28 | 14 | 14 |
| Logo card, padding 12 | 28 | 12 | 16 |
| Tab card, padding 8 | 20 | 8 | 12 |
| Segmented control, padding 4 | 16 | 4 | 12 |
| Switch, knob inset 4 | 16 | 4 | 12 |

Never nest a radius larger than its container allows. If the math goes below 4, use square-ish 4–6 rather than mismatched curves.

### Standalone radii

| Element | Radius |
|---|---|
| Sheet top corners, floating panels | 28 |
| Content cards and grouped lists | 20 |
| Source selector cards | 18 |
| Tiles, stat boxes | 16 |
| Chips | 12 |
| Covers, large | 12 |
| Covers, list thumbnails | 8 |
| Pill buttons | height / 2 |
| App icon | 22.6% of size, a squircle on Android adaptive icons |

## Elevation

Mostly flat. Shadows only on things that float over content:

- Floating nav and bars: `0 12px 30px rgba(21,22,26,0.28)`
- Floating cards over web content: `0 16px 40px rgba(0,0,0,0.35)`
- Hero covers: `0 18px 36px rgba(0,0,0,0.35)`

## Components

- **Bottom nav:** floating ink pill, 16 from the sides, 20 from the bottom, 68 tall, radius 28, padding 10. Four items, 64×48, radius 18. Active item is a white fill with ink icon. Inactive icons are white at 70%.
- **Browser bar:** replaces the nav inside the browser. Ink pill, radius 34, padding 0 10. Back, address pill in `ink2` with a shield count badge, tabs count, overflow.
- **Chips:** 40 tall, radius 12, 16 horizontal padding. Selected is ink with white text, others `surfaceMuted`.
- **Grouped list:** white card, radius 20, 16 horizontal padding, rows at least 56 tall with `surfaceSunken` dividers.
- **Switch:** 52×32 track, radius 16. Knob 24, inset 4. On is `ink`, off is `border`. Not accent.
- **Primary button:** accent fill, white text, 54 tall pill when standalone. Inside a card, radius follows the nesting rule.
- **Sheet:** white, top radius 28, 40×5 grabber, 16 side padding, scrim `rgba(14,15,18,0.55)`.
- **Glass bar:** see the `glass` token. Used on the reader and over web content only.

## Iconography

24px grid, 2px stroke, round caps and joins, `currentColor`. A Lucide-style set fits. No filled icons except play and the more-dots.

## Motion

Not designed yet. Intended direction: shared-element cover transitions from library to series to reader, bars that fade and slide on reader tap, undo snackbars instead of confirm dialogs.

## Brand

The logo is **Progress A**: an A whose crossbar is a half-filled progress bar in the brand gradient. Files are in `design/brand/`. On dark grounds use `ascon-mark-white.svg`. Minimum size 24px; below that, drop the unfilled track and keep the gradient fill.
