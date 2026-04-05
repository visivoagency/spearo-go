# Spearo Go — Full Session Summary
**Date:** April 5, 2026
**Session:** Sprint 0 + Sprint 1 foundation
**Status:** Ready to open in Xcode

---

## What was built in this session

A complete, compilable Apple Watch app for spearfishers — from zero to a fully structured, on-brand, Xcode-ready project in one session. No third-party dependencies. No API keys. Works globally.

---

## How to open in Xcode right now

```
1. Finder → ~/Documents/spearo-go/
2. Double-click SpearoGo.xcodeproj
3. Xcode opens → set your Team (Signing & Capabilities)
4. Scheme: SpearoGo → Apple Watch Series 9 (45mm)
5. ⌘R to build and run
```

First-launch checklist in Xcode:
- [ ] Signing & Capabilities → Team → select your Apple ID
- [ ] Confirm deployment target is watchOS 10.0
- [ ] Run on simulator to verify data loads

---

## Complete file inventory

```
spearo-go/
├── SpearoGo.xcodeproj/               ← Open this in Xcode
│   ├── project.pbxproj               ← Generated — all 29 files wired in
│   └── project.xcworkspace/
│       ├── contents.xcworkspacedata
│       └── xcshareddata/
│           └── IDEWorkspaceChecks.plist
│
├── SpearoGo/
│   ├── SpearoGoApp.swift             ← @main entry, WKApplicationDelegate, 30-min background refresh
│   ├── AppState.swift                ← @Observable coordinator, refresh pipeline, location override
│   ├── ContentView.swift             ← 5-page TabView, wires saved locations into AppState
│   ├── Info.plist                    ← Bundle ID, location permission, dark mode lock
│   │
│   ├── Views/
│   │   ├── VerdictPage.swift         ← GO/MAYBE/SKETCHY/NO GO + score ring (animated)
│   │   ├── ConditionsPage.swift      ← Wind speed/dir, swell height/period
│   │   ├── WaterPage.swift           ← Sea temp, visibility, wetsuit tip
│   │   ├── TidesPage.swift           ← Next high/low times + tide direction
│   │   ├── FishActivityPage.swift    ← Moon phase, solunar major/minor periods
│   │   └── LocationsView.swift       ← Saved locations CRUD + AddLocationView sheet
│   │
│   ├── Models/
│   │   ├── WeatherData.swift         ← Wind, gusts, visibility struct
│   │   ├── MarineData.swift          ← Wave height, period, direction, SST struct
│   │   ├── TideData.swift            ← High/low times, phase, direction struct
│   │   ├── SolunarData.swift         ← Moon phase, illumination, periods struct
│   │   ├── DiveScore.swift           ← Verdict enum + weighted DiveScore.calculate()
│   │   └── SavedLocation.swift       ← @Model (SwiftData) — name, lat, lon, isActive
│   │
│   ├── Services/
│   │   ├── WeatherService.swift      ← Open-Meteo /v1/forecast (URLSession, no key)
│   │   ├── MarineService.swift       ← Open-Meteo /v1/marine (URLSession, no key)
│   │   ├── TideService.swift         ← Synthetic M2+S2 harmonic tide calculator (offline)
│   │   ├── SolunarService.swift      ← Meeus orbital math — moon, sun, solunar periods (offline)
│   │   ├── LocationService.swift     ← CoreLocation, @Observable, GPS fallback
│   │   ├── ScoreService.swift        ← Weighted composite score (Weather 30%, Marine 30%, Tides 15%, Solunar 25%)
│   │   └── CacheService.swift        ← actor, 30-min TTL, keyed by lat/lon grid
│   │
│   ├── Utils/
│   │   ├── Brand.swift               ← ALL design tokens (Colors, Typography, Spacing, Radius, Opacity)
│   │   ├── Typography.swift          ← Text extensions (.verdictStyle, .dataValueStyle, .sectionHeaderStyle, etc.)
│   │   ├── Modifiers.swift           ← ViewModifiers (.brandPage, .brandCard, .verdictChip, .infoPill, .brandLoading)
│   │   ├── Constants.swift           ← Non-visual constants: API URLs, score weights, app meta
│   │   ├── PersonalityCopy.swift     ← All verdict messages (GO×8, MAYBE×7, SKETCHY×8, NO GO×8, loading×4)
│   │   └── PreviewHelpers.swift      ← .previewAsWatch(), AppState.preview(), MockData, WatchSize enum
│   │
│   └── Assets.xcassets/
│       ├── Contents.json
│       ├── AppIcon.appiconset/       ← Slots ready — add PNG artwork (see icon sizes below)
│       │   └── Contents.json
│       └── Colors/
│           ├── Background/           ← #000000
│           ├── OceanBlue/            ← #0077B6 (primary accent)
│           ├── Teal/                 ← #00B4D8 (secondary accent)
│           ├── TextPrimary/          ← #FFFFFF
│           ├── TextSecondary/        ← #6B7D8E
│           ├── VerdictGo/            ← #2ECC71
│           ├── VerdictMaybe/         ← #F39C12
│           ├── VerdictSketchy/       ← #E67E22
│           └── VerdictNoGo/          ← #E74C3C
│
├── mockups/
│   └── index.html                    ← Interactive HTML mockups (open in browser)
│
├── docs/
│   ├── SESSION_SUMMARY.md            ← This file
│   ├── SPRINT_PLAN.md                ← Full sprint roadmap (Sprint 0–3)
│   ├── ARCHITECTURE.md               ← System diagram, data flow, concurrency model
│   ├── DESIGN_SYSTEM.md              ← Color palette, typography scale, component specs
│   ├── SCORE_ALGORITHM.md            ← Weighted scoring breakdown + worked example
│   ├── API_REFERENCE.md              ← Open-Meteo endpoints, cache strategy, rate limits
│   ├── USER_FLOWS.md                 ← 7 user flows (cold start, offline, GPS fallback, etc.)
│   └── XCODE_SETUP.md                ← Step-by-step Xcode wiring guide
│
├── Package.swift                     ← SPM definition (watchOS 10 target)
├── generate_xcodeproj.py             ← Run to regenerate SpearoGo.xcodeproj if needed
├── README.md                         ← Project overview, tech stack, setup instructions
└── .gitignore
```

**Total:** 47 files, ~4,800 lines of Swift + documentation

---

## Architecture in one diagram

```
User taps / app opens
        │
        ▼
  AppState.refresh()          @Observable — single state owner
        │
   ┌────┴────────────────┐
   │                     │
   ▼                     ▼
CacheService          LocationService
(actor, 30-min TTL)   (CoreLocation)
   │                     │
   │ miss                │ coord
   ▼                     ▼
WeatherService      Active coordinate
MarineService       = saved location
(URLSession)          OR live GPS
(Open-Meteo)          OR San Diego (default)
   │
   ▼
TideService.calculate()     ← offline, ~1ms
SolunarService.calculate()  ← offline, ~1ms
   │
   ▼
ScoreService.score()
   Weather×0.30 + Marine×0.30 + Tides×0.15 + Solunar×0.25
   │
   ▼
DiveScore → Verdict (GO / MAYBE / SKETCHY / NO GO)
   │
   ▼
@MainActor → SwiftUI re-renders all 5 pages
```

---

## Score algorithm

| Signal | Weight | Source | Key factor |
|--------|--------|--------|-----------|
| Weather | **30%** | Open-Meteo weather API | Wind speed (knots) |
| Marine | **30%** | Open-Meteo marine API | Wave height (metres) |
| Solunar | **25%** | Offline orbital math | Moon phase + period proximity |
| Tides | **15%** | Offline harmonic calc | Flood/slack/ebb phase |

| Score | Verdict | Color |
|-------|---------|-------|
| 8–10 | GO | `#2ECC71` |
| 6–7 | MAYBE | `#F39C12` |
| 4–5 | SKETCHY | `#E67E22` |
| 0–3 | NO GO | `#E74C3C` |

---

## APIs used

| API | URL | Key required | Global |
|-----|-----|-------------|--------|
| Open-Meteo Weather | `api.open-meteo.com/v1/forecast` | No | Yes |
| Open-Meteo Marine | `marine-api.open-meteo.com/v1/marine` | No | Yes (ocean only) |
| Tides | Offline math | — | Yes |
| Solunar | Offline math | — | Yes |

API validation confirmed working for: San Diego, Sydney, Marseille, Bali, Cape Town, Cancun.

---

## Design system

### Colors (all in Assets.xcassets — use `Brand.Colors.*` in code)

| Token | Hex | Use |
|-------|-----|-----|
| `Brand.Colors.background` | `#000000` | Every page background |
| `Brand.Colors.primary` | `#0077B6` | Icons, rings, buttons |
| `Brand.Colors.secondary` | `#00B4D8` | Highlights, tide heights |
| `Brand.Colors.textPrimary` | `#FFFFFF` | All data values |
| `Brand.Colors.textSecondary` | `#6B7D8E` | Labels, units, captions |
| `Brand.Colors.go` | `#2ECC71` | GO verdict |
| `Brand.Colors.maybe` | `#F39C12` | MAYBE verdict |
| `Brand.Colors.sketchy` | `#E67E22` | SKETCHY verdict |
| `Brand.Colors.noGo` | `#E74C3C` | NO GO verdict |

### Rule: never hardcode hex in views
```swift
// ✅ Always
.foregroundStyle(Brand.Colors.primary)
Color("OceanBlue")

// ❌ Never
.foregroundStyle(Color(hex: "#0077B6"))
```

### Key ViewModifiers
```swift
.brandPage()              // Black background, fills screen
.brandCard()              // Subtle dark card with border
.infoPill()               // Teal info capsule (wetsuit tip, notes)
.verdictChip(color:)      // Verdict badge with matching tint
.brandSectionHeader()     // Section title style + spacing
.brandLoading(isLoading:) // Spinner overlay
```

### Key Text extensions
```swift
Text("GO").verdictStyle(color: Brand.Colors.go)
Text("14").dataValueStyle()
Text("WIND").itemLabelStyle()
Text("kn").unitStyle()
Text("CONDITIONS").brandSectionHeader()
```

---

## How to use Previews

Every view has `#Preview` macros at the bottom. To preview:

```swift
// Single page, specific verdict state
#Preview("GO") {
    VerdictPage()
        .previewAsWatch()                          // 184×224pt, black bg, rounded
        .environment(AppState.preview(verdict: .go))
}

// All 4 verdict states side by side
#Preview("All Verdicts") {
    AllVerdictsPreview()
}

// Watch sizes available
.previewAsWatch(size: .mm41)   // 176×215pt
.previewAsWatch(size: .mm44)   // 184×224pt  ← default
.previewAsWatch(size: .mm45)   // 198×242pt
.previewAsWatch(size: .mm49)   // 205×251pt (Ultra)
```

**Mock data** lives in `PreviewHelpers.swift → MockData`. Modify it there if you need different test values — changes flow to all previews automatically.

---

## App Icon — what's needed

The `AppIcon.appiconset` slots are wired. You need PNG artwork at:

| Purpose | Size | Required for |
|---------|------|-------------|
| App launcher (44mm Watch) | 100×100px @2x = 200×200px | Running on device |
| App launcher (45mm Watch) | 102×102px @2x = 204×204px | Series 9/10 |
| App launcher (Ultra) | 108×108px @2x = 216×216px | Ultra |
| Notification | 66×66px @2x = 132×132px | Notifications |
| Quick Look | 88×88px @2x = 176×176px | Siri/complications |
| App Store marketing | 1024×1024px @1x | App Store submission |

Design brief:
- Background: `#000000` or `#0077B6`
- Icon: speargun or fish silhouette — reads at 44px
- No transparency, no rounded corners (watchOS clips them automatically)
- All artwork drops into `SpearoGo/Assets.xcassets/AppIcon.appiconset/` and is referenced by name in `Contents.json`

---

## Personality copy — full list

### GO (score 8–10)
1. "GET IN THE WATER!"
2. "Fish are waiting. Go get 'em."
3. "Perfect day. No excuses."
4. "Why are you still reading this? GO!"
5. "The ocean is calling."
6. "Conditions are chef's kiss."
7. "Today's the day. Suit up."
8. "Send it!"

### MAYBE (score 6–7)
1. "Could be worse. Could be better."
2. "Eh, you've dove in worse."
3. "Decent. Just don't be a hero."
4. "Your call, chief."
5. "Not ideal, but fishable."
6. "The ocean shrugs at you."
7. "Proceed with mild enthusiasm."

### SKETCHY (score 4–5)
1. "Think twice, dive once."
2. "Your wetsuit will earn its keep today."
3. "Spicy conditions. You sure?"
4. "Only if you're feeling brave."
5. "The ocean is in a mood."
6. "Experienced divers only."
7. "Tell someone where you're going."
8. "Check your insurance first."

### NO GO (score 0–3)
1. "Nope. Netflix day."
2. "The ocean said no."
3. "Stay dry. Stay alive."
4. "Not today, friend."
5. "Hard pass."
6. "Your couch misses you anyway."
7. "Train your breath hold instead."
8. "Even the fish are hiding."

### Loading
1. "Asking the ocean..."
2. "Checking the vibes..."
3. "Consulting the fish..."
4. "Reading the waves..."

---

## Sprint status

### Sprint 0 — Foundation ✅ Complete
- Project structure + GitHub repo
- Package.swift (watchOS 10)
- All 5 page views (shell)
- All models, services, utils
- Constants + PersonalityCopy
- API validation spike (6 global locations)
- Full documentation suite
- Interactive HTML mockups

### Sprint 1 — Core build ✅ Complete
- `SpearoGo.xcodeproj` generated and committed
- `Assets.xcassets` with 9 named color sets
- `Brand.swift` design token system
- `Typography.swift` text style extensions
- `Modifiers.swift` reusable ViewModifiers
- `PreviewHelpers.swift` + MockData
- `Info.plist`
- All views migrated to `Brand.*` tokens
- `LocationsView.swift` — saved locations CRUD
- `AppState` — saved location override + GPS fallback chain
- `ContentView` — wires SwiftData locations into AppState
- `SpearoGoApp` — background refresh every 30 min via `WKApplicationRefreshBackgroundTask`
- `#Preview` macros on every view

### Sprint 2 — Polish (next)
- [ ] Score ring spring animation (scaffolded, needs `.animation` trigger)
- [ ] Haptic feedback on verdict change (`WKHapticType.success / .failure`)
- [ ] Smart Stack complication
- [ ] Digital Crown scrolling on Tides + Fish pages
- [ ] Loading skeletons / shimmer placeholders
- [ ] Stale cache indicator ("> 30 min ago")
- [ ] VoiceOver labels on all views

### Sprint 3 — App Store (final)
- [ ] App Icon artwork (all 6 sizes)
- [ ] App Store screenshots (6 watch sizes)
- [ ] App Store description + keywords
- [ ] Privacy policy URL
- [ ] TestFlight build
- [ ] App Store submission

---

## Known Xcode first-run notes

1. **Signing:** You must set a Team before the simulator will build. Xcode → Target → Signing & Capabilities → Team.

2. **SwiftData on first run:** If the simulator crashes on launch, delete the app from the simulator and rebuild. SwiftData schema migrations are not yet configured.

3. **GPS in simulator:** The watch simulator defaults to Apple HQ (Cupertino). Go to Simulator → Features → Location → Custom Location to test a specific coordinate.

4. **Marine API miss:** If you test with a landlocked coordinate, the marine API returns a 400. The app falls back to a neutral marine score (5.0) and continues.

5. **Background refresh in simulator:** Background tasks don't fire automatically in the simulator. Test by calling `AppState().refresh()` directly from Xcode's debug console.

6. **Re-generating the project file:** If you add new Swift files, run `python3 generate_xcodeproj.py` from the repo root to regenerate the `.xcodeproj`. Then re-open in Xcode. Alternatively, just drag new files into the Xcode project navigator as normal.

---

## Git log (this session)

```
28baa26  Sprint 1: xcodeproj, Brand token migration, saved locations, background refresh
9229f5f  Add Xcode brand system: xcassets, Brand.swift, Typography, Modifiers, PreviewHelpers
898014a  Add full documentation suite and interactive UI mockups
f0be317  Sprint 0: project scaffold, all services, API validation spike
```

---

## Quick reference — file purposes at a glance

| If you want to... | Go to... |
|-------------------|----------|
| Change a color | `Assets.xcassets/Colors/` + `Brand.swift` |
| Change a font size | `Brand.swift → Typography` |
| Add a new verdict message | `PersonalityCopy.swift` |
| Tune score weights | `Constants.swift → Weights` |
| Tune scoring logic | `ScoreService.swift` |
| Add a new page | New file in `Views/`, add `.tag(5)` to TabView in `ContentView.swift` |
| Change API parameters | `WeatherService.swift` or `MarineService.swift` |
| Adjust cache TTL | `CacheService.swift → ttl` |
| Change tide model accuracy | `TideService.swift` |
| Change solunar calculation | `SolunarService.swift` |
| Add a new saved location field | `SavedLocation.swift` (add SwiftData migration) |
| Add a new preview | Bottom of any View file, use `AppState.preview()` |
| See all mockups | Open `mockups/index.html` in browser |
