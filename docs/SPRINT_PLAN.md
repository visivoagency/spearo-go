# Spearo Go — Sprint Plan

## Product overview
**Spearo Go** is a standalone Apple Watch app (watchOS 10+) that gives spearfishers an instant, opinionated dive-day verdict — GO / MAYBE / SKETCHY / NO GO — by combining weather, marine, tide, and solunar data into a single weighted score.

- **Price:** $2.99 one-time purchase
- **Platform:** Apple Watch (standalone, no iPhone dependency)
- **Min OS:** watchOS 10
- **Audience:** Global — all APIs must work internationally

---

## Score algorithm

| Signal   | Weight | Source |
|----------|--------|--------|
| Weather  | 30%    | Open-Meteo forecast API |
| Marine   | 30%    | Open-Meteo marine API |
| Tides    | 15%    | Synthetic lunar calculator (offline) |
| Solunar  | 25%    | Sun/moon orbital math (offline) |

**Verdict bands:**
- 8–10 → GO (green `#2ECC71`)
- 6–7  → MAYBE (amber `#F39C12`)
- 4–5  → SKETCHY (orange `#E67E22`)
- 0–3  → NO GO (red `#E74C3C`)

---

## Sprint 0 — Foundation ✅

- [x] Project structure & GitHub repo
- [x] Package.swift (watchOS 10 target)
- [x] App entry point + TabView shell (5 pages)
- [x] All 5 page views (Verdict, Conditions, Water, Tides, Fish Activity)
- [x] Models: WeatherData, MarineData, TideData, SolunarData, DiveScore, SavedLocation
- [x] Services: Weather, Marine, Tide, Solunar, Location, Score, Cache
- [x] Constants (colors, weights, API URLs)
- [x] PersonalityCopy (all verdict messages)
- [x] AppState @Observable coordinator
- [x] API validation spike (6 global locations)

---

## Sprint 1 — Xcode project + Core data flow ✅

- [x] Create Xcode project (watchOS App, SwiftUI, SwiftData)
- [x] Wire SPM sources into Xcode target
- [x] Live GPS → coordinate flow
- [x] End-to-end data fetch → score → verdict display
- [x] Saved locations (SwiftData CRUD)
- [x] 30-minute cache with background refresh

---

## Sprint 2 — Polish & UX ✅

- [x] Loading skeletons / shimmer (all data pages: Conditions, Water, Tides, Fish Activity)
- [x] Haptic feedback on verdict change (success/click/directionUp/failure per verdict)
- [x] Haptic on first load (click) and on error (failure)
- [x] Digital Crown scroll on Tides + Fish Activity pages (ScrollView + focusable + digitalCrownRotation)
- [x] VoiceOver accessibility labels on all views (Verdict, Conditions, Water, Tides, Fish, Locations)
- [x] Stale cache indicator ("Just now" / "X min ago" / "Stale" — turns orange when >30min)
- [x] Long-press gesture on Verdict page → opens Locations sheet
- [x] Last-refreshed timestamp tracking in AppState
- [x] `#if os(watchOS)` guards for WatchKit APIs (SPM compatibility)
- [ ] Complications (Smart Stack widget) — deferred to Sprint 3
- [ ] Onboarding flow (first launch) — deferred to Sprint 3

---

## Sprint 3 — Store prep ✅

- [x] Fix DiveScore.swift bug — `Constants.Colors.Verdict` → `Brand.Colors.forVerdict()`
- [x] Onboarding flow — 3-page swipeable welcome (Welcome → How It Works → Location + "Let's Go")
- [x] `@AppStorage("hasCompletedOnboarding")` gate in ContentView
- [x] Smart Stack complication widget (WidgetKit)
  - SharedScore model for app → widget data via UserDefaults (App Group)
  - AppState writes score + reloads widget timeline after each refresh
  - AccessoryRectangular: verdict + score gauge + relative time
  - AccessoryCircular: score gauge with fish icon
  - AccessoryCorner: score number + gauge arc (watchOS only)
  - Widget previews for all families
  - NOTE: Widget extension target must be added via Xcode GUI
- [x] App Store metadata file (name, subtitle, description, keywords, category, pricing)
- [x] Privacy policy view — accessible from Locations sheet, covers location/data/analytics
- [x] Version info in Locations view
- [ ] App Icon (all sizes) — requires PNG artwork
- [ ] Screenshot set (6 sizes) — requires simulator
- [ ] TestFlight beta — requires Xcode GUI
- [ ] App Store submission — requires Xcode GUI

---

## API notes

Both APIs are **free, no API key, global coverage**:

- Weather: `https://api.open-meteo.com/v1/forecast`
- Marine:  `https://marine-api.open-meteo.com/v1/marine`

Rate limits are generous for a single-user watch app (10k req/day free tier).
Tide and solunar are pure offline math — zero network dependency, instant.

---

## Architecture decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| State management | `@Observable` macro | watchOS 10 native, no ObservableObject boilerplate |
| Persistence | SwiftData | First-class watchOS 10 support |
| Networking | URLSession | No external deps; watch standalone |
| Tide data | Synthetic harmonic | No API key/cost; ±30min accuracy sufficient for UX |
| Solunar | Orbital math (Meeus) | No API key/cost; deterministic |
