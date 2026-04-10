# Spearo Go — Wear OS

Standalone Wear OS port of [Spearo Go](../README.md), the dive-day verdict app for spearfishers. Targets **Galaxy Watch 4+** (Wear OS 3, API 30+).

## Overview

Spearo Go aggregates weather, marine, tide, and solunar data into a single opinionated verdict: **GO / MAYBE / SKETCHY / NO GO**. This Wear OS version is a 1:1 feature port of the watchOS app, built with Kotlin and Jetpack Compose for Wear OS.

## Architecture

```
┌─────────────────────────────────────────────────┐
│           Presentation Layer                    │
│   Jetpack Compose for Wear OS (5-page pager)   │
├─────────────────────────────────────────────────┤
│              State Layer                        │
│     AppViewModel + StateFlow (Hilt)             │
├─────────────────────────────────────────────────┤
│              Service Layer                      │
│  Weather | Marine | Tide | Solunar | Score |    │
│  Cache   | Location | RefreshWorker             │
├─────────────────────────────────────────────────┤
│             Data Sources                        │
│  Open-Meteo APIs (Retrofit, no keys)            │
│  Offline Math (Tides, Solunar)                  │
│  Room DB (SavedLocation persistence)            │
│  DataStore (preferences)                        │
└─────────────────────────────────────────────────┘
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose for Wear OS |
| DI | Hilt |
| Networking | Retrofit + OkHttp |
| Persistence | Room |
| Location | FusedLocationProviderClient (Play Services) |
| Background | WorkManager (30-min periodic refresh) |
| Tiles | Wear OS Tiles API |
| Concurrency | Kotlin Coroutines + StateFlow |

## Project Structure

```
wear/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/spearotracker/spearogo/
│       │   ├── SpearoGoApp.kt          # @HiltAndroidApp
│       │   ├── MainActivity.kt         # Entry point + permission launcher
│       │   ├── di/
│       │   │   └── AppModule.kt        # Hilt providers (Room, DAO)
│       │   ├── models/
│       │   │   ├── WeatherData.kt      # Wind, visibility, cloud cover
│       │   │   ├── MarineData.kt       # Waves, SST
│       │   │   ├── TideData.kt         # Heights, phases, times
│       │   │   ├── SolunarData.kt      # Moon, activity periods
│       │   │   ├── DiveScore.kt        # Composite score + verdict enum
│       │   │   └── SavedLocation.kt    # Room entity for dive spots
│       │   ├── services/
│       │   │   ├── WeatherService.kt   # Open-Meteo weather API
│       │   │   ├── MarineService.kt    # Open-Meteo marine API
│       │   │   ├── TideService.kt      # Offline M2+S2 harmonic math
│       │   │   ├── SolunarService.kt   # Offline Meeus orbital math
│       │   │   ├── ScoreService.kt     # Weighted composite scoring
│       │   │   ├── CacheService.kt     # In-memory cache (30-min TTL)
│       │   │   ├── LocationService.kt  # FusedLocationProvider wrapper
│       │   │   ├── RefreshWorker.kt    # WorkManager periodic refresh
│       │   │   ├── AppDatabase.kt      # Room database
│       │   │   ├── LocationDao.kt      # Room DAO for saved locations
│       │   │   └── ServiceException.kt
│       │   ├── ui/
│       │   │   ├── SpearoGoWearApp.kt  # Root composable (pager + nav)
│       │   │   ├── AppViewModel.kt     # ViewModel + UI state
│       │   │   ├── theme/
│       │   │   │   ├── Brand.kt        # Design tokens (colors, type, spacing)
│       │   │   │   └── Theme.kt        # Material 3 theme wrapper
│       │   │   ├── pages/
│       │   │   │   ├── VerdictPage.kt      # Verdict + score ring
│       │   │   │   ├── ConditionsPage.kt   # Wind + swell
│       │   │   │   ├── WaterPage.kt        # SST + visibility + wetsuit tip
│       │   │   │   ├── TidesPage.kt        # High/low + phase + direction
│       │   │   │   ├── FishActivityPage.kt # Moon + solunar periods
│       │   │   │   └── OnboardingScreen.kt # 3-page first-launch flow
│       │   │   └── components/
│       │   │       └── ConditionItem.kt    # Reusable data display widget
│       │   ├── tiles/
│       │   │   └── SpearoGoTileService.kt  # Watch face tile (placeholder)
│       │   └── utils/
│       │       ├── Constants.kt        # API URLs, score weights
│       │       └── PersonalityCopy.kt  # 27+ verdict messages
│       └── res/
│           ├── drawable/tile_preview.xml
│           ├── mipmap-*/ic_launcher.png  # Spearo Vision "S" icon
│           └── values/strings.xml
├── build.gradle.kts                    # Root build config
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## Features (1:1 with watchOS)

- **Dive Verdict** — GO / MAYBE / SKETCHY / NO GO based on composite score (0-10)
- **5-Page Swipe UI** — Verdict, Conditions, Water, Tides, Fish Activity
- **Score Ring** — Animated circular progress with spring animation
- **Saved Locations** — CRUD dive spots stored locally (Room)
- **GPS Fallback** — Live GPS → saved spot → San Diego default
- **Background Refresh** — WorkManager every 30 minutes
- **Offline Capable** — Tide & solunar math computed on-device
- **Haptic Feedback** — Vibration on verdict changes
- **Onboarding** — 3-page first-launch flow with location permission
- **Watch Tile** — Quick-glance verdict on watch face

## Scoring Algorithm

```
composite = (weather × 0.30) + (marine × 0.30) + (tides × 0.15) + (solunar × 0.25)
```

| Score | Verdict |
|-------|---------|
| 8.0-10.0 | GO |
| 6.0-7.9 | MAYBE |
| 4.0-5.9 | SKETCHY |
| 0.0-3.9 | NO GO |

## APIs

- **Weather:** `api.open-meteo.com/v1/forecast` (free, no key)
- **Marine:** `marine-api.open-meteo.com/v1/marine` (free, no key)
- **Tides:** Computed offline (M2+S2 harmonic model)
- **Solunar:** Computed offline (Meeus orbital math)

## Build & Run

1. Open `wear/` folder in Android Studio
2. Sync Gradle
3. Select Wear OS emulator (API 30+) or Galaxy Watch 4+ device
4. Run

## Distribution

- **Package:** `com.spearotracker.spearogo`
- **Standalone:** Yes (no phone companion required)
- **Target:** Wear OS 3+ (Galaxy Watch 4+)
- **Price:** $2.99
