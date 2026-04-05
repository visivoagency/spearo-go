# Spearo Go

> Your dive-day verdict on your wrist.

**Spearo Go** is a standalone Apple Watch app for spearfishers. It pulls weather, marine, tide, and solunar data and returns a single, opinionated verdict: **GO / MAYBE / SKETCHY / NO GO** — with personality.

---

## Features

- 5-page horizontal TabView (swipe through conditions)
- Weighted composite score from 4 data sources
- Offline tide calculator (synthetic harmonic model)
- Offline solunar calculator (Meeus orbital math)
- Saved locations via SwiftData
- 30-minute cache — works in poor connectivity
- Pure black OLED UI — battery-friendly on Apple Watch
- Standalone — no iPhone required

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

- **Platform:** watchOS 10+ standalone
- **Language:** Swift 5.9
- **UI:** SwiftUI
- **State:** `@Observable` macro
- **Persistence:** SwiftData
- **Networking:** URLSession
- **Package manager:** Swift Package Manager

---

## Project Structure

```
SpearoGo/
├── SpearoGoApp.swift          # @main entry, modelContainer
├── AppState.swift             # @Observable coordinator
├── ContentView.swift          # 5-page TabView
├── Views/
│   ├── VerdictPage.swift      # Score ring + personality copy
│   ├── ConditionsPage.swift   # Wind + swell
│   ├── WaterPage.swift        # Temp + viz + wetsuit tip
│   ├── TidesPage.swift        # High/low times + direction
│   └── FishActivityPage.swift # Moon + solunar periods
├── Models/
│   ├── WeatherData.swift
│   ├── MarineData.swift
│   ├── TideData.swift
│   ├── SolunarData.swift
│   ├── DiveScore.swift        # Verdict enum + weighted calc
│   └── SavedLocation.swift    # @Model (SwiftData)
├── Services/
│   ├── WeatherService.swift   # Open-Meteo weather fetch
│   ├── MarineService.swift    # Open-Meteo marine fetch
│   ├── TideService.swift      # Synthetic lunar tide calculator
│   ├── SolunarService.swift   # Moon/sun position math
│   ├── LocationService.swift  # CoreLocation + saved spots
│   ├── ScoreService.swift     # Weighted composite scorer
│   └── CacheService.swift     # 30-min in-memory TTL cache
└── Utils/
    ├── Constants.swift        # Colors, weights, API URLs
    └── PersonalityCopy.swift  # All verdict messages
```

---

## Setup

### Prerequisites
- Xcode 15+
- Apple Watch Series 4+ (watchOS 10)
- macOS 14+ (Sonoma)

### Build

```bash
git clone https://github.com/visivoagency/spearo-go.git
cd spearo-go
open SpearoGo.xcodeproj   # (created in Sprint 1)
```

Select the **SpearoGo Watch App** scheme → your Apple Watch simulator or device → **Run**.

### API validation spike

Run the validation script to confirm all 6 global locations return data:

```bash
swift docs/api_validation_spike.swift
```

Expected output: weather + marine data for San Diego, Sydney, Marseille, Bali, Cape Town, Cancun; synthetic tide heights; solunar ratings.

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

---

## Roadmap

See [docs/SPRINT_PLAN.md](docs/SPRINT_PLAN.md) for the full sprint breakdown.

- **Sprint 0** — Foundation ✅
- **Sprint 1** — Xcode project + live data flow
- **Sprint 2** — Polish, complications, haptics
- **Sprint 3** — App Store submission

---

## Pricing

$2.99 one-time purchase. No subscription, no ads, no data collection.

---

## License

Proprietary — © Visivo Agency. All rights reserved.
