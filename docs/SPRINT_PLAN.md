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

## Sprint 0 — Foundation (current)

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

## Sprint 1 — Xcode project + Core data flow

- [ ] Create Xcode project (watchOS App, SwiftUI, SwiftData)
- [ ] Wire SPM sources into Xcode target
- [ ] Live GPS → coordinate flow
- [ ] End-to-end data fetch → score → verdict display
- [ ] Saved locations (SwiftData CRUD)
- [ ] 30-minute cache with background refresh

---

## Sprint 2 — Polish & UX

- [ ] Loading skeletons / shimmer
- [ ] Haptic feedback on verdict change
- [ ] Complications (Smart Stack widget)
- [ ] Digital Crown scroll on Tides / Solunar pages
- [ ] Accessibility: Dynamic Type, VoiceOver labels
- [ ] Onboarding flow (first launch)

---

## Sprint 3 — Store prep

- [ ] App Icon (all sizes)
- [ ] Screenshot set (6 sizes)
- [ ] App Store description + keywords
- [ ] Privacy policy (location only, no tracking)
- [ ] TestFlight beta
- [ ] App Store submission

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
