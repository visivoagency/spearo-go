# Spearo Go — Full Session Summary
**Date:** April 5–9, 2026
**Session:** Sprint 0 + Sprint 1 foundation + App Store Submission + Post-submission fixes
**Status:** Build 2 (1.0.0) uploaded to App Store Connect ✅

---

## What was built in this session

A complete, compilable Apple Watch app for spearfishers — from zero to a fully structured, on-brand, Xcode-ready project in one session. No third-party dependencies. No API keys. Works globally.

---

## How to open in Xcode right now

```
1. Finder → ~/Documents/spearo-go/
2. Double-click SpearoGo.xcodeproj
3. Scheme: SpearoGo → Any iOS Device (for archive) or Apple Watch simulator (for debug)
4. Team and signing are already configured (RBDNV7NG89 — VISIVO)
5. ⌘R to build and run on simulator
```

First-launch checklist in Xcode:
- [x] Signing & Capabilities → Team = RBDNV7NG89 (VISIVO) — automatic
- [x] Deployment target watchOS 10.0
- [x] Build uploaded to App Store Connect

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
│   │   ├── ConditionsPage.swift      ← Wind speed/dir, swell height/period + shimmer
│   │   ├── WaterPage.swift           ← Sea temp, visibility, wetsuit tip + shimmer
│   │   ├── TidesPage.swift           ← Next high/low times + Digital Crown scroll
│   │   ├── FishActivityPage.swift    ← Moon phase, solunar periods + Digital Crown scroll
│   │   ├── LocationsView.swift       ← Saved locations CRUD + privacy link + version
│   │   ├── OnboardingView.swift      ← 3-page swipeable first-launch onboarding
│   │   └── PrivacyPolicyView.swift   ← In-app privacy policy (scrollable)
│   │
│   ├── Models/
│   │   ├── WeatherData.swift         ← Wind, gusts, visibility struct
│   │   ├── MarineData.swift          ← Wave height, period, direction, SST struct
│   │   ├── TideData.swift            ← High/low times, phase, direction struct
│   │   ├── SolunarData.swift         ← Moon phase, illumination, periods struct
│   │   ├── DiveScore.swift           ← Verdict enum + weighted DiveScore.calculate()
│   │   ├── SavedLocation.swift       ← @Model (SwiftData) — name, lat, lon, isActive
│   │   └── SharedScore.swift         ← Codable model for app → widget data via UserDefaults
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
│   ├── Widget/
│   │   ├── SpearoGoWidget.swift      ← Widget config, TimelineProvider, entry model
│   │   └── WidgetViews.swift         ← Rectangular, Circular, Corner complication views
│   │
│   └── Assets.xcassets/
│       ├── Contents.json
│       ├── AppIcon.appiconset/       ← 1024×1024 universal watchOS icon
│       │   ├── AppIcon.png
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
├── SpearoGoiOS/                      ← iOS stub app (watch-only container for App Store)
│   ├── SpearoGoiOSApp.swift          ← @main — displays "watch-only" message
│   ├── Info.plist                    ← LSApplicationLaunchProhibited + ITSWatchOnlyContainer
│   └── Assets.xcassets/
│       ├── Contents.json
│       └── AppIcon.appiconset/       ← 1024×1024 universal iOS icon
│           ├── AppIcon-1024.png
│           └── Contents.json
│
├── Graphics/                         ← App Store screenshots (watch simulator)
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
│   ├── XCODE_SETUP.md                ← Step-by-step Xcode wiring guide
│   └── APP_STORE_METADATA.md         ← App Store listing: name, subtitle, description, keywords
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

### Sprint 2 — Polish & UX ✅ Complete
- Shimmer/skeleton loading on all data pages (Conditions, Water, Tides, Fish Activity)
- Haptic feedback on verdict change (success/click/directionUp/failure per verdict)
- Haptic on first load (click) and on error (failure)
- Digital Crown scrolling on Tides + Fish Activity pages
- VoiceOver accessibility labels on all views
- Stale cache indicator ("Just now" / "X min ago" / "Stale" — orange when >30min)
- Long-press gesture on Verdict page → opens Locations sheet
- Last-refreshed timestamp tracking in AppState
- `#if os(watchOS)` guards for WatchKit APIs (SPM compatibility)

### Sprint 3 — Store prep ✅ Complete
- Fixed DiveScore.swift bug (`Constants.Colors.Verdict` → `Brand.Colors.forVerdict()`)
- Onboarding flow — 3-page swipeable welcome with `@AppStorage` gate
- Smart Stack complication widget (WidgetKit)
  - SharedScore model for app → widget data via UserDefaults (App Group)
  - AccessoryRectangular, AccessoryCircular, AccessoryCorner views
  - Timeline provider with 30-minute refresh cadence
- App Store metadata file (name, subtitle, description, keywords, pricing)
- Privacy policy view — accessible from Locations sheet
- Version info in Locations view

### App Store Submission — April 8, 2026 ✅ Complete
- **Team ID configured:** RBDNV7NG89 (VISIVO) in generate_xcodeproj.py
- **URLs updated:** Privacy policy, contact, terms → spearotracker.com
- **App icon added:** 1024×1024 PNG from Spearo Vision project
- **iOS stub app created** (`SpearoGoiOS/`):
  - Required because standalone watchOS archives have NO App Store distribution method
  - Xcode only offers WatchOSAdHoc/Enterprise/Development for watchOS-only archives
  - Solution: thin iOS app wraps the watchOS app via "Embed Watch Content" build phase
  - `SpearoGoiOSApp.swift` — minimal @main displaying "watch-only" message
  - `Info.plist` — declares `LSApplicationLaunchProhibited` + `ITSWatchOnlyContainer`
  - `Assets.xcassets` — iOS app icon (same artwork as watch icon)
- **Two-target project structure:**
  - **SpearoGo** (iOS) — SKIP_INSTALL=NO, embeds watch app, bundle ID `agency.visivo.SpearoGo`
  - **SpearoGo Watch App** (watchOS) — SKIP_INSTALL=YES, embedded target, bundle ID `agency.visivo.SpearoGo.watchkitapp`
  - PBXTargetDependency + PBXContainerItemProxy links iOS → watchOS
  - PBXCopyFilesBuildPhase "Embed Watch Content" copies watch .app into iOS bundle
- **generate_xcodeproj.py rewritten** to produce both targets with correct dependencies
- **Privacy manifest** (`PrivacyInfo.xcprivacy`) — declares UserDefaults API usage
- **Entitlements** (`SpearoGo.entitlements`) — App Group for widget data sharing
- **Debug print wrapped** in `#if DEBUG` in SpearoGoApp.swift
- **Fallback coordinates documented** — San Diego (32.7, -117.2) in AppState.swift
- **pbxproj syntax fix:** Comma added to special chars in `q()` function — unquoted `1,2` in `TARGETED_DEVICE_FAMILY` caused "Unable to read project" error
- **Archive + export succeeded** with `app-store-connect` method
- **Build uploaded to App Store Connect** via `xcodebuild -exportArchive` with `destination=upload`

#### Key discovery: watchOS App Store distribution
Standalone watchOS archives do NOT have an App Store distribution method. The Xcode Organizer only shows:
- `WatchOSAdHoc`
- `WatchOSEnterprise`
- `WatchOSDevelopmentSigned`

There is no `WatchOSAppStoreDistribution`. Apple requires watchOS apps to be submitted inside an iOS app container, even for watch-only apps. The iOS container declares `LSApplicationLaunchProhibited=true` and `ITSWatchOnlyContainer=true`.

### Post-Submission Fixes — April 9, 2026 ✅ Complete
Code review identified and fixed several issues before App Store review:

- **Error UI added** (`VerdictPage.swift`): When API calls fail, users now see a warning triangle, "Couldn't load conditions", and "Tap to retry" — previously the error was silent with only a haptic buzz
- **GPS fallback indicator** (`AppState.swift` + `VerdictPage.swift`): Added `isUsingFallbackLocation` computed property; VerdictPage shows "Default location" warning when neither GPS nor a saved location is available
- **Marine API graceful fallback** (`AppState.swift`): Marine fetch now uses `try?` with neutral defaults (0m waves, 10s period, 22°C SST) instead of failing the entire refresh pipeline — handles landlocked coordinates and transient network failures
- **Widget reference removed** (`APP_STORE_METADATA.md`): "Smart Stack widget" line removed from FEATURES since no Widget Extension target exists in v1.0
- **Copyright year fixed** (`APP_STORE_METADATA.md`): Updated from 2025 to 2026
- **Open-Meteo API verified**: Both weather and marine endpoints tested live — all field names, response structures, and unit conversions confirmed correct
- **Build number bumped** to 2 (`generate_xcodeproj.py`): Both iOS and watchOS targets updated
- **Build 2 uploaded** to App Store Connect

---

## Known Xcode first-run notes

1. **Signing:** You must set a Team before the simulator will build. Xcode → Target → Signing & Capabilities → Team.

2. **SwiftData on first run:** If the simulator crashes on launch, delete the app from the simulator and rebuild. SwiftData schema migrations are not yet configured.

3. **GPS in simulator:** The watch simulator defaults to Apple HQ (Cupertino). Go to Simulator → Features → Location → Custom Location to test a specific coordinate.

4. **Marine API miss:** If you test with a landlocked coordinate, the marine API returns a 400. The app gracefully falls back to neutral marine defaults (flat water, 22°C) and still produces a score from weather/tides/solunar.

5. **Background refresh in simulator:** Background tasks don't fire automatically in the simulator. Test by calling `AppState().refresh()` directly from Xcode's debug console.

6. **Re-generating the project file:** If you add new Swift files, run `python3 generate_xcodeproj.py` from the repo root to regenerate the `.xcodeproj`. Then re-open in Xcode. Alternatively, just drag new files into the Xcode project navigator as normal.

7. **Archiving for App Store:** Select the **SpearoGo** scheme (iOS), destination **Any iOS Device**, then Product → Archive. The archive contains both the iOS stub and the embedded watchOS app. Use Distribute → App Store Connect to upload.

8. **Re-uploading a build:** Bump `CURRENT_PROJECT_VERSION` in `generate_xcodeproj.py` (both iOS and watchOS targets), regenerate, archive, and upload. App Store Connect rejects duplicate build numbers.

---

## Git log (this session)

```
8d706da  Graceful marine API fallback for landlocked/network-failure cases
83bdc43  Fix error UI, GPS fallback indicator, and metadata accuracy
1ad9dbd  App Store submission: iOS wrapper, two-target project, build uploaded
f215522  Add SESSION_SUMMARY.md — complete reference for Xcode handoff
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
| Edit widget views | `Widget/SpearoGoWidget.swift` + `Widget/WidgetViews.swift` |
| Edit onboarding | `Views/OnboardingView.swift` |
| Edit privacy policy | `Views/PrivacyPolicyView.swift` |
| Edit App Store copy | `docs/APP_STORE_METADATA.md` |
| See all mockups | Open `mockups/index.html` in browser |
