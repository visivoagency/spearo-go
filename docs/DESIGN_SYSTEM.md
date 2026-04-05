# Spearo Go — Design System

## Philosophy

**Dark. Dense. Direct.** Every pixel is ocean-black. Information is surfaced in teal and blue. Verdicts command attention with saturated signal colors. No gradients, no shadows, no decoration that doesn't carry data.

The watch face is 44–45mm. Assume the user is squinting into sunlight with wet hands.

---

## Color palette

### Base

| Token | Hex | Usage |
|-------|-----|-------|
| `background` | `#000000` | Pure black — OLED, zero battery drain |
| `textPrimary` | `#FFFFFF` | All data values, primary labels |
| `textSecondary` | `#6B7D8E` | Units, subtitles, timestamps, section headers |

### Brand accents

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `primaryAccent` | `#0077B6` | 0, 119, 182 | Icons, score ring track, interactive |
| `secondaryAccent` | `#00B4D8` | 0, 180, 216 | Tide heights, highlights, active states |

### Verdict signal colors

| Verdict | Token | Hex | Score range |
|---------|-------|-----|-------------|
| GO | `Verdict.go` | `#2ECC71` | 8.0 – 10.0 |
| MAYBE | `Verdict.maybe` | `#F39C12` | 6.0 – 7.9 |
| SKETCHY | `Verdict.sketchy` | `#E67E22` | 4.0 – 5.9 |
| NO GO | `Verdict.noGo` | `#E74C3C` | 0.0 – 3.9 |

### Contrast ratios (WCAG AA on #000000)

| Color | Ratio | Pass |
|-------|-------|------|
| `#FFFFFF` (text primary) | 21:1 | ✅ AAA |
| `#2ECC71` (GO) | 7.2:1 | ✅ AAA |
| `#F39C12` (MAYBE) | 6.5:1 | ✅ AA |
| `#E67E22` (SKETCHY) | 5.1:1 | ✅ AA |
| `#E74C3C` (NO GO) | 4.8:1 | ✅ AA |
| `#00B4D8` (secondary) | 6.1:1 | ✅ AA |
| `#0077B6` (primary) | 3.9:1 | ✅ AA (large text) |
| `#6B7D8E` (text secondary) | 3.2:1 | ✅ AA (large text) |

---

## Typography

watchOS uses SF Compact — all system fonts.

| Role | Font | Size | Weight | Usage |
|------|------|------|--------|-------|
| Verdict label | SF Compact | 18pt | Black (900) | "GO", "NO GO" |
| Data value | SF Compact | 18pt | Bold (700) | Wind speed, wave height |
| Section header | SF Compact | 10pt | Semibold (600) | "CONDITIONS", "TIDES" |
| Personality copy | SF Compact | 11pt | Regular (400) | Verdict sub-message |
| Unit label | SF Compact | 9pt | Regular (400) | "kn", "m", "°C" |
| Small label | SF Compact | 8pt | Medium (500) | "WIND", "HIGH", "LOW" |
| Caption | SF Compact | 9pt | Regular (400) | Tide times, periods |

**Letter spacing:** Section headers use `kerning(1.5)` for readability at small sizes.

---

## Spacing

Apple Watch screen: 176 × 215pt (44mm) / 198 × 242pt (45mm)

| Token | Value | Usage |
|-------|-------|-------|
| `pagePadding` | 12pt | View edge padding |
| `sectionGap` | 10pt | Between major elements |
| `itemGap` | 6pt | Between related items |
| `microGap` | 2–4pt | Labels + values |

---

## Component library

### Score Ring

```
Outer ring: 50×50pt
Track: #6B7D8E at 30% opacity, strokeWidth 5
Fill: verdict color, trim 0→score/10, lineCap .round
Center: score value, 14pt Bold
Rotation: -90° (starts at 12 o'clock)
```

### Condition Item

```
VStack, minWidth 55pt
  Icon: SF Symbol, 14pt, primaryAccent
  Label: 8pt Semibold, textSecondary, kerning 0.8
  Value: 18pt Bold, textPrimary
  Unit: 9pt, textSecondary (inline)
```

### Tide Event

```
VStack, spacing 2pt
  Label: 8pt Semibold, textSecondary, kerning 1
  Time: system .time style, 14pt Bold, textPrimary
  Height: caption2, secondaryAccent
```

### Solunar Period Row

```
HStack
  Label: 8pt Semibold, verdict color, kerning 1, w=42
  Time: 12pt Medium, textPrimary
  Spacer
  Dot: circle.fill, 5pt, verdict color
```

---

## Navigation model

```
TabView(.page) — horizontal swipe
├── Page 1: Verdict      ● ○ ○ ○ ○
├── Page 2: Conditions   ○ ● ○ ○ ○
├── Page 3: Water        ○ ○ ● ○ ○
├── Page 4: Tides        ○ ○ ○ ● ○
└── Page 5: Fish         ○ ○ ○ ○ ●
```

- Page indicator dots shown by system (watchOS .page style)
- Tap anywhere on Verdict page → refresh
- Digital Crown → vertical scroll within page (Sprint 2)
- Swipe from screen edge → previous/next page

---

## Icon system

All icons use SF Symbols. No custom artwork in v1.

| Context | Symbol | Color |
|---------|--------|-------|
| Wind | `wind` | primaryAccent |
| Swell | `water.waves` | primaryAccent |
| Wind direction | `arrow.up.right` | primaryAccent |
| Wave period | `timer` | primaryAccent |
| Temperature | `thermometer.medium` | primaryAccent |
| Visibility | `eye` | primaryAccent |
| Tide rising | `arrow.up.circle.fill` | Verdict.maybe |
| Tide falling | `arrow.down.circle.fill` | primaryAccent |
| Fish activity | `fish.fill` | primaryAccent |
| Moon | varies by phase | textPrimary |
| Loading | `ProgressView` | primaryAccent |

---

## Motion

- `ProgressView` spinner on load — system default tint `primaryAccent`
- No custom animations in Sprint 0
- Sprint 2: score ring draws on with `.animation(.spring(duration: 0.6))`
- Sprint 2: verdict label scales in with `.transition(.scale.combined(with: .opacity))`

---

## Accessibility

- All `ConditionItem` icons include implicit `accessibilityLabel` via SF Symbols
- Score ring: `accessibilityValue(String(format: "Score %.1f out of 10", score))`
- Verdict text is always visible alongside color — color is never the sole signal
- VoiceOver order: section header → values → units (Sprint 2)
- Dynamic Type: all fonts use `.font(.system(size:weight:))` — Sprint 2 converts to `.body`, `.caption` semantic sizes

---

## Dark / light mode

App is **always dark**. `Color(hex: "#000000")` is hardcoded. There is no light mode — this is intentional. OLED black is a feature, not a bug.
