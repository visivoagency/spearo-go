# Spearo Go

> Your dive-day verdict on your wrist.

**Spearo Go** is a standalone Apple Watch app for spearfishers. It pulls weather, marine, tide, and solunar data and returns a single, opinionated verdict: **GO / MAYBE / SKETCHY / NO GO** — with personality.

**Status:** v1.0.0 (Build 2) submitted to App Store Connect — pending Apple review.

---

## Features

- One-tap verdict — GO, MAYBE, SKETCHY, or NO GO
- Composite score from 0 to 10 with animated score ring
- 5-page horizontal TabView: Verdict, Conditions, Water, Tides, Fish Activity
- Save multiple dive spots and switch between them
- Background refresh every 30 minutes — always current
- Offline tide and solunar calculations — no API key needed
- Haptic feedback when conditions change
- Fully accessible with VoiceOver support
- 3-page swipeable onboarding flow
- Error UI with tap-to-retry
- GPS fallback indicator when no saved location or GPS is available
- Graceful marine API fallback for landlocked/offline scenarios
- No subscriptions, no ads, no account required

---

## Pages

| # | Page | Data |
|---|------|------|
| 1 | **Verdict** | GO / MAYBE / SKETCHY / NO GO + composite score ring |
| 2 | **Conditions** | Wind speed/direction, swell height/period |
| 3 | **Water** | Sea surface temp, estimated visibility, wetsuit tip |
| 4 | **Tides** | Next high/low, tide direction & phase |
| 5 | **Fish Activity** | Solunar rating, moon phase, major/minor periods |

---

## Score Algorithm

| Signal   | Weight | API |
|----------|--------|-----|
| Weather  | 30%    | [Open-Meteo](https://open-meteo.com) — free, no key |
| Marine   | 30%    | [Open-Meteo Marine](https://marine-api.open-meteo.com) — free, no key |
| Tides    | 15%    | Synthetic lunar harmonic — offline |
| Solunar  | 25%    | Sun/moon orbital math (Meeus) — offline |

**Verdict bands:** GO (8–10) · MAYBE (6–7) · SKETCHY (4–5) · NO GO (0–3)

---

## Tech Stack

- **Platform:** watchOS 10+ standalone (watch-only, no iPhone companion)
- **Language:** Swift 5.9
- **UI:** SwiftUI
- **State:** `@Observable` macro
- **Persistence:** SwiftData (saved locations)
- **Networking:** URLSession (HTTPS, no API keys)
- **Dependencies:** None — zero third-party packages
- **Distribution:** iOS stub wrapper with `LSApplicationLaunchProhibited` + `ITSWatchOnlyContainer`

---

## Project Structure

```
SpearoGo/
├── SpearoGoApp.swift          # @main entry, WKApplicationDelegate, 30-min background refresh
├── AppState.swift             # @Observable coordinator, refresh pipeline, location override
├── ContentView.swift          # 5-page TabView, wires saved locations into AppState
├── Info.plist                 # Bundle ID, location permission, dark mode lock
├── PrivacyInfo.xcprivacy      # Privacy manifest (UserDefaults API usage)
├── SpearoGo.entitlements      # App Group for widget data sharing
│
├── Views/
│   ├── VerdictPage.swift      # Score ring + personality copy + error UI
│   ├── ConditionsPage.swift   # Wind + swell + shimmer loading
│   ├── WaterPage.swift        # Temp + viz + wetsuit tip + shimmer
│   ├── TidesPage.swift        # High/low times + Digital Crown scroll
│   ├── FishActivityPage.swift # Moon + solunar periods + Digital Crown scroll
│   ├── LocationsView.swift    # Saved locations CRUD + privacy link + version
│   ├── OnboardingView.swift   # 3-page swipeable first-launch onboarding
│   └── PrivacyPolicyView.swift# In-app privacy policy (scrollable)
│
├── Models/
│   ├── WeatherData.swift      # Wind, gusts, visibility struct
│   ├── MarineData.swift       # Wave height, period, direction, SST struct
│   ├── TideData.swift         # High/low times, phase, direction struct
│   ├── SolunarData.swift      # Moon phase, illumination, periods struct
│   ├── DiveScore.swift        # Verdict enum + weighted scoring
│   ├── SavedLocation.swift    # @Model (SwiftData) — name, lat, lon, isActive
│   └── SharedScore.swift      # Codable model for app → widget data via UserDefaults
│
├── Services/
│   ├── WeatherService.swift   # Open-Meteo /v1/forecast (URLSession, no key)
│   ├── MarineService.swift    # Open-Meteo /v1/marine (URLSession, no key)
│   ├── TideService.swift      # Synthetic M2+S2 harmonic tide calculator (offline)
│   ├── SolunarService.swift   # Meeus orbital math — moon, sun, solunar periods (offline)
│   ├── LocationService.swift  # CoreLocation, @Observable, GPS fallback
│   ├── ScoreService.swift     # Weighted composite score (W30% M30% T15% S25%)
│   └── CacheService.swift     # actor, 30-min TTL, keyed by lat/lon grid
│
├── Utils/
│   ├── Brand.swift            # ALL design tokens (Colors, Typography, Spacing, Radius, Opacity)
│   ├── Typography.swift       # Text extensions (.verdictStyle, .dataValueStyle, etc.)
│   ├── Modifiers.swift        # ViewModifiers (.brandPage, .brandCard, .infoPill, etc.)
│   ├── Constants.swift        # Non-visual constants: API URLs, score weights, app meta
│   ├── PersonalityCopy.swift  # 27 verdict messages + 4 loading messages
│   └── PreviewHelpers.swift   # .previewAsWatch(), AppState.preview(), MockData
│
├── Widget/
│   ├── SpearoGoWidget.swift   # Widget config, TimelineProvider, entry model
│   └── WidgetViews.swift      # Rectangular, Circular, Corner complication views
│
└── Assets.xcassets/           # 9 named color sets + AppIcon (1024×1024)

SpearoGoiOS/                   # iOS stub app (watch-only container for App Store)
├── SpearoGoiOSApp.swift       # @main — displays "watch-only" message
├── Info.plist                 # LSApplicationLaunchProhibited + ITSWatchOnlyContainer
└── Assets.xcassets/           # iOS app icon
```

---

## Setup

### Prerequisites
- Xcode 15+
- Apple Watch Series 4+ (watchOS 10)
- macOS 14+ (Sonoma)

### Build & Run

```bash
git clone https://github.com/visivoagency/spearo-go.git
cd spearo-go
open SpearoGo.xcodeproj
```

1. Scheme: **SpearoGo** → Apple Watch simulator → **Run** (for debug)
2. Scheme: **SpearoGo** → Any iOS Device → Product → **Archive** (for App Store)

Team and signing are pre-configured (RBDNV7NG89 — VISIVO).

### Regenerating the Xcode project

If you add new Swift files, run from the repo root:

```bash
python3 generate_xcodeproj.py
```

Then re-open `SpearoGo.xcodeproj` in Xcode.

### API validation spike

Confirm all 6 global locations return data:

```bash
swift docs/api_validation_spike.swift
```

---

## Color System

| Role | Hex | Usage |
|------|-----|-------|
| Background | `#000000` | Pure black — OLED battery |
| Primary accent | `#0077B6` | Ocean blue — icons, rings |
| Secondary accent | `#00B4D8` | Teal — highlights |
| GO | `#2ECC71` | Green verdict |
| MAYBE | `#F39C12` | Amber verdict |
| SKETCHY | `#E67E22` | Orange verdict |
| NO GO | `#E74C3C` | Red verdict |
| Text primary | `#FFFFFF` | Values, labels |
| Text secondary | `#6B7D8E` | Subtitles, units |

All colors are in `Assets.xcassets` — reference via `Brand.Colors.*` in code.

---

## Privacy

- Location data is used **only** to fetch weather/marine conditions — never stored or shared
- No analytics, no tracking, no user accounts
- Open-Meteo APIs are free and keyless — no API secrets in the codebase
- Privacy manifest (`PrivacyInfo.xcprivacy`) declares UserDefaults API usage
- Full privacy policy at [spearotracker.com/privacy-policy](https://spearotracker.com/privacy-policy)

---

## App Store

- **Price:** $2.99 (one-time, Tier 1)
- **Category:** Sports / Weather
- **Age Rating:** 4+
- **Bundle ID:** `agency.visivo.SpearoGo`
- **App Store metadata:** See [docs/APP_STORE_METADATA.md](docs/APP_STORE_METADATA.md)

---

## Documentation

| Doc | Contents |
|-----|----------|
| [SESSION_SUMMARY.md](docs/SESSION_SUMMARY.md) | Full build log, architecture, design system, sprint history |
| [SPRINT_PLAN.md](docs/SPRINT_PLAN.md) | Sprint roadmap (Sprint 0–3) |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System diagram, data flow, concurrency model |
| [DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | Color palette, typography scale, component specs |
| [SCORE_ALGORITHM.md](docs/SCORE_ALGORITHM.md) | Weighted scoring breakdown + worked example |
| [API_REFERENCE.md](docs/API_REFERENCE.md) | Open-Meteo endpoints, cache strategy, rate limits |
| [USER_FLOWS.md](docs/USER_FLOWS.md) | 7 user flows (cold start, offline, GPS fallback, etc.) |
| [XCODE_SETUP.md](docs/XCODE_SETUP.md) | Step-by-step Xcode wiring guide |
| [APP_STORE_METADATA.md](docs/APP_STORE_METADATA.md) | App Store listing: name, subtitle, description, keywords |

---

## License

Proprietary — © 2026 Visivo Agency. All rights reserved.
