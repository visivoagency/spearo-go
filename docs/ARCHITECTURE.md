# Spearo Go — Technical Architecture

## Overview

Spearo Go is a **fully standalone Apple Watch app** — no iPhone companion app, no WatchConnectivity. All data fetching, caching, computation, and persistence runs entirely on the watch.

```
┌─────────────────────────────────────────────────────┐
│                   Apple Watch                        │
│                                                     │
│  ┌──────────┐   ┌──────────┐   ┌─────────────────┐ │
│  │  SwiftUI │   │AppState  │   │   Services      │ │
│  │  Views   │◄──│@Observable│◄──│  Layer          │ │
│  │  (5 pg)  │   │          │   │                 │ │
│  └──────────┘   └──────────┘   └────────┬────────┘ │
│                                          │          │
│  ┌──────────────────────────────────────┼────────┐ │
│  │            Data Sources              │        │ │
│  │                                      ▼        │ │
│  │  ┌────────────┐  ┌────────────┐  ┌────────┐  │ │
│  │  │Open-Meteo  │  │Open-Meteo  │  │Offline │  │ │
│  │  │ Weather API│  │ Marine API │  │ Math   │  │ │
│  │  │(URLSession)│  │(URLSession)│  │Tide+Sol│  │ │
│  │  └────────────┘  └────────────┘  └────────┘  │ │
│  └──────────────────────────────────────────────┘ │
│                                                     │
│  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │   SwiftData      │  │      CacheService        │ │
│  │  SavedLocation   │  │   (actor, 30-min TTL)    │ │
│  └──────────────────┘  └──────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

---

## Layer breakdown

### Presentation layer — SwiftUI Views

| File | Role |
|------|------|
| `ContentView.swift` | Root `TabView(.page)` — horizontal swipe navigation |
| `VerdictPage.swift` | Primary screen: verdict label + score ring |
| `ConditionsPage.swift` | Wind speed/dir + swell height/period |
| `WaterPage.swift` | SST + estimated visibility + wetsuit recommendation |
| `TidesPage.swift` | Next high/low + tide direction + phase |
| `FishActivityPage.swift` | Moon phase + solunar major/minor periods |

All views are **read-only** consumers of `AppState` via `@Environment`. No view owns data.

### State layer — AppState

`AppState` is an `@Observable` class (not `ObservableObject`) that:
- Owns all fetched data (`WeatherData`, `MarineData`, `TideData`, `SolunarData`, `DiveScore`)
- Orchestrates the fetch/cache/compute pipeline in `refresh()`
- Exposes `isLoading` and `error` for UI state
- Holds a `LocationService` instance

```
refresh() flow:
  1. requestLocation() → CoreLocation
  2. cache hit? → use cached WeatherData / MarineData
  3. cache miss? → URLSession fetch → store in cache
  4. tideService.calculate() → synchronous, offline
  5. solunarService.calculate() → synchronous, offline
  6. scoreService.score() → weighted composite
  7. @MainActor publish all results
```

### Service layer

| Service | Network | Notes |
|---------|---------|-------|
| `WeatherService` | Yes | Open-Meteo `/v1/forecast` |
| `MarineService` | Yes | Open-Meteo marine `/v1/marine` |
| `TideService` | No | Synthetic M2+S2 harmonic |
| `SolunarService` | No | Meeus orbital math |
| `LocationService` | No | CoreLocation, `@Observable` |
| `ScoreService` | No | Pure function, value type |
| `CacheService` | No | `actor`, keyed by lat/lon grid |

### Persistence layer — SwiftData

`SavedLocation` is the only persistent model:

```swift
@Model final class SavedLocation {
    var id: UUID
    var name: String
    var latitude: Double
    var longitude: Double
    var createdAt: Date
    var isActive: Bool
}
```

The `modelContainer` is attached at the `WindowGroup` level in `SpearoGoApp`.

---

## Data flow diagram

```
User opens app / taps screen
         │
         ▼
  AppState.refresh()
         │
    ┌────┴────┐
    │         │
    ▼         ▼
CoreLocation  CacheService
(coordinate)  (hit/miss?)
    │              │
    │         miss │  hit ────────────┐
    │              │                  │
    │              ▼                  │
    │      URLSession.data()          │
    │      Open-Meteo APIs            │
    │              │                  │
    │              ▼                  │
    │        Decode JSON              │
    │              │                  │
    │         store in cache          │
    │              │◄─────────────────┘
    │              │
    └──────┬───────┘
           │
           ▼
   TideService.calculate()    ← pure math, synchronous
   SolunarService.calculate() ← pure math, synchronous
           │
           ▼
   ScoreService.score()
   DiveScore.calculate()      ← weighted composite
           │
           ▼
    @MainActor publish
           │
           ▼
    SwiftUI re-renders
```

---

## Concurrency model

- `AppState.refresh()` is `async` — called via `.task {}` or `.onTapGesture`
- `CacheService` is an `actor` — thread-safe reads/writes, no data races
- `LocationService` uses `CLLocationManagerDelegate` callbacks, published on `@MainActor`
- JSON decoding happens off the main thread inside `URLSession.data()`
- All SwiftUI state mutations are dispatched to `@MainActor`

---

## Offline capabilities

| Feature | Online required? |
|---------|-----------------|
| Tide times & heights | No — synthetic harmonic |
| Solunar periods | No — orbital math |
| Moon phase & illumination | No — orbital math |
| Weather (wind, gusts) | Yes — Open-Meteo |
| Marine (waves, SST) | Yes — Open-Meteo |
| Score & verdict | Only if no cached data |

**With cached data:** full app works offline for 30 minutes after last fetch.

---

## Error handling

```
Network failure
    → AppState.error set
    → Views render cached data if available
    → "Tap to refresh" prompt

GPS unavailable
    → Falls back to last saved location
    → Falls back to default (San Diego 32.7/-117.2)

Marine API miss (landlocked coord)
    → HTTP 400 → ServiceError.badResponse
    → ScoreService uses fallback neutral marine score
```

---

## watchOS-specific considerations

- **No WatchConnectivity** — no iPhone dependency whatsoever
- **Background app refresh** — scheduled via `WKApplicationRefreshBackgroundTask` (Sprint 1)
- **Complications** — Smart Stack widget reads from shared `AppStorage` (Sprint 2)
- **Display always-on** — Verdict page designed to be readable in AOD
- **Digital Crown** — TidesPage and FishActivityPage will use `focusable()` + crown input (Sprint 2)
- **Storage** — SwiftData DB is tiny (coordinates + strings only); cache is in-memory, cleared on app kill

---

## Dependencies

**Zero external dependencies.** Everything ships with the OS or is computed from first principles:

| Capability | How |
|-----------|-----|
| HTTP | `URLSession` (Foundation) |
| JSON | `Codable` (Foundation) |
| Location | `CoreLocation` |
| Persistence | `SwiftData` |
| UI | `SwiftUI` |
| Math | Swift stdlib + Darwin |
