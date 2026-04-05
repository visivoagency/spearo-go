# Spearo Go — User Flows

## Core personas

### Alex — Weekend Spearfisher
42, dives 2–3x per month at local reefs. Checks conditions on the drive to the beach. Wants a fast, confident "yes or no" without reading three different apps. Values personality and brevity.

### Maya — Travelling Spearfisher
28, trips to Bali, Azores, South Africa. Needs to check unfamiliar spots where she has no local knowledge. Saves locations, compares between sessions.

### Rui — Safety-first Diver
55, dives alone occasionally. Wants to know about sketchy conditions before he's already at the water. Respects the NO GO verdict.

---

## Flow 1 — First open (cold start)

```
User raises wrist / opens app
         │
         ▼
SpearoGoApp loads
ModelContainer initialised (SwiftData)
         │
         ▼
ContentView appears
TabView shows Page 1 (Verdict)
         │
         ▼
.task {} → AppState.refresh()
isLoading = true
         │
         ▼
Verdict page shows:
  ProgressView spinner
  "Asking the ocean..." (random)
         │
   ┌─────┴──────────────────────────────┐
   │ GPS available              GPS unavailable
   │                                    │
   ▼                                    ▼
CLLocation resolves            Default coord used
(~2–5s on watch)              San Diego fallback
   │                                    │
   └─────────────┬──────────────────────┘
                 │
                 ▼
        Fetch Weather + Marine
        (parallel URLSession, ~1–3s)
                 │
                 ▼
        Compute Tide + Solunar
        (synchronous, <1ms)
                 │
                 ▼
        ScoreService.score()
        DiveScore.calculate()
                 │
                 ▼
        @MainActor publish results
                 │
                 ▼
        Verdict page renders:
          "GO" / "MAYBE" / "SKETCHY" / "NO GO"
          Personality message
          Score ring (animated in Sprint 2)
```

**Expected total time:** 3–8 seconds on good connectivity.

---

## Flow 2 — Checking conditions (warm cache)

```
User raises wrist (within 30 min of last fetch)
         │
         ▼
AppState.refresh() called
         │
         ▼
CacheService.cachedWeather() → hit ✓
CacheService.cachedMarine()  → hit ✓
         │
         ▼
Tide + Solunar recomputed (fresh, ~1ms)
         │
         ▼
Results render immediately
No spinner shown
```

**Expected total time:** <100ms.

---

## Flow 3 — Swiping through pages

```
Page 1: Verdict
  User sees: "MAYBE" + "Eh, you've dove in worse."
  Score ring at 6.4
         │
  swipe right ─────────────────────────────────┐
                                               │
Page 2: Conditions                             │
  Wind: 14kn NE                                │
  Swell: 1.2m / 8s                             │
         │                                     │
  swipe right                           swipe left
         │                                     │
Page 3: Water                                  │
  Temp: 22°C / Good viz                        │
  "Comfortable. 3mm."                          │
         │                                     │
  swipe right                                  │
         │                                     │
Page 4: Tides                                  │
  HIGH: 14:32 (1.8m)                           │
  LOW:  08:14 (0.3m)                           │
  ↑ Flood                                      │
         │                                     │
  swipe right                                  │
         │                                     │
Page 5: Fish Activity                          │
  Moon: 86% ○ / Good                           │
  MAJOR: 14:47                                 │
  MINOR: 11:23                                 │
         │                                     │
  swipe right → bounces back (end of pages)    │
         │                                     │
  swipe left ──────────────────────────────────┘
```

---

## Flow 4 — Refreshing stale data

```
User taps anywhere on Verdict page
         │
         ▼
AppState.refresh() called
         │
         ▼
CacheService: TTL expired → cache miss
         │
         ▼
Shows spinner while fetching
         │
         ▼
New data arrives → verdict may change
Personality message re-randomises
```

---

## Flow 5 — Saving a location (Sprint 1)

```
Force touch / long press on any page
         │
         ▼
"Save Location" option appears
         │
         ▼
User confirms
         │
         ▼
SavedLocation created in SwiftData:
  name = reverse-geocoded placename
  coordinate = current GPS
         │
         ▼
Location appears in saved list
```

---

## Flow 6 — Switching saved location (Sprint 1)

```
Saved locations list (Sprint 1 screen)
         │
User taps saved location
         │
         ▼
SavedLocation.isActive = true
         │
         ▼
AppState uses saved coordinate
(overrides GPS)
         │
         ▼
Data refreshes for new location
```

---

## Flow 7 — Offline / no connectivity

```
User opens app, no internet
         │
         ▼
AppState.refresh() called
         │
         ▼
Cache check:
  ─ Hit (< 30 min old): serve cached → full app works
  ─ Miss: URLSession throws URLError
         │
         ▼ (cache miss)
AppState.error set
         │
         ▼
Verdict page shows last known score (if any)
  + "Data may be outdated" indicator (Sprint 2)

OR if no prior data:
  "Couldn't reach the ocean. Try again."
  Tap to retry
```

---

## Error states

| Condition | UI |
|-----------|-----|
| Loading | Spinner + random loading message |
| No data + no cache | Error message + tap to retry |
| Stale cache (>30m) + no network | Show cached data + visual staleness indicator |
| GPS denied | "Enable location in Settings" prompt |
| Marine API miss (landlocked) | Marine score = neutral, rest of score shown |

---

## Notification flows (Sprint 2)

```
Background refresh fires (every 30 min via WKApplicationRefreshBackgroundTask)
         │
         ▼
Fetch new conditions
         │
  ┌──────┴──────────────────────────────┐
  │ Verdict changed             No change
  │                                     │
  ▼                                     ▼
Push local notification              Silent update
"Conditions improved → GO!"          Cache updated
"Heads up: NO GO now"
```
