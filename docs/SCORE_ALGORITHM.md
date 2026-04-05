# Spearo Go — Score Algorithm

## Overview

The dive-day score is a **weighted composite** of four independent signals, each scored 0–10, then combined into a final 0–10 score that maps to a verdict.

```
Composite = (Weather × 0.30) + (Marine × 0.30) + (Tides × 0.15) + (Solunar × 0.25)
```

---

## Verdict bands

| Range | Verdict | Color | Meaning |
|-------|---------|-------|---------|
| 8.0 – 10.0 | **GO** | `#2ECC71` | Drop everything. Get in. |
| 6.0 – 7.9 | **MAYBE** | `#F39C12` | Fishable but not perfect. |
| 4.0 – 5.9 | **SKETCHY** | `#E67E22` | Experienced divers only. |
| 0.0 – 3.9 | **NO GO** | `#E74C3C` | Stay dry. |

---

## 1. Weather score (30%)

Source: Open-Meteo `/v1/forecast` — `wind_speed_10m`, `wind_gusts_10m`, `visibility`

```
Base score: 10

Wind speed (converted m/s → knots):
  0–9 kn     → −0    (flat calm to light air: ideal)
  10–14 kn   → −1    (gentle breeze: minor chop)
  15–19 kn   → −3    (moderate breeze: manageable)
  20–24 kn   → −5    (fresh breeze: marginal)
  25+ kn     → −8    (strong breeze+: don't go)

Gust penalty:
  gusts > windSpeed + 10 kn → −1  (unstable, snappy surface)

Visibility:
  < 5 km     → −2    (poor surface visibility)
  > 15 km    → +0.5  (crystal conditions)

Clamp: max(0, min(10, result))
```

**Rationale:** Spearfishing surface conditions are dominated by wind-driven chop. Gusty conditions are worse than steady winds of the same speed. High visibility = better underwater clarity proxy.

---

## 2. Marine score (30%)

Source: Open-Meteo Marine API — `wave_height`, `wave_period`, `sea_surface_temperature`

```
Base score: 10

Wave height (significant wave height Hs):
  0–0.49 m   → −0    (flat/glassy: excellent)
  0.5–0.99 m → −1    (small swell: easy)
  1.0–1.49 m → −2.5  (moderate: manageable)
  1.5–1.99 m → −4    (rough: challenging)
  2.0–2.49 m → −6    (very rough: marginal)
  2.5+ m     → −9    (dangerous)

Wave period modifier:
  period > 14s → +0.5  (long-period groundswell: organised, less turbulent)
  period < 6s  → −1    (short choppy seas: disorganised, tiring)

Sea surface temperature:
  < 12°C     → −1    (extreme cold: physiological risk)
  > 30°C     → −0.5  (very warm: reduced O2, potential stinger bloom)

Clamp: max(0, min(10, result))
```

**Rationale:** Wave height is the primary marine hazard for spearfishers. Long-period swell is predictable and less fatiguing than short, chaotic chop of the same height.

---

## 3. Tide score (15%)

Source: Synthetic harmonic calculator (offline, no API)

```
Tide phase → score:
  Slack water  → 9.0   (max visibility, fish holding position)
  Flood (in)   → 7.5   (incoming carries nutrients, fish active)
  Ebb (out)    → 6.0   (outgoing can reduce visibility)
```

**Rationale:** Slack water maximises underwater visibility (suspended sediment settles) and fish tend to hold feeding positions rather than actively swimming. Flooding tide brings baitfish and nutrients inshore, activating predators. Ebb carries silt and often drops visibility near river mouths.

**Note on tidal range:** The synthetic calculator uses a global M2+S2 harmonic model. Tidal range varies enormously by location (0.1m in the Med vs 12m in the Bay of Fundy). The phase classification is more reliable than absolute heights for global applicability.

---

## 4. Solunar score (25%)

Source: Offline orbital math (Meeus algorithms)

```
Base score: 5

Moon phase contribution (up to +3):
  score += (1 - distance_from_full_or_new) × 3

  New moon (phase=0)  → +3.0  (maximum gravitational effect)
  Full moon (phase=0.5) → +3.0  (maximum gravitational effect)
  Quarter moons → +0.0

Proximity to major period (moon transit/anti-transit):
  < 30 min   → +2.5  (peak activity window)
  30–59 min  → +1.5  (approaching peak)
  60–119 min → +0.5  (active window)
  120+ min   → +0    (between peaks)

Proximity to minor period (moonrise/moonset):
  < 30 min   → +0.5  (minor activity window)

Clamp: max(0, min(10, result))
```

**Rationale:** John Alden Knight's solunar theory (1926) correlates fish feeding activity with lunar transit times. New and full moons produce the greatest gravitational tidal forces. Major periods (moon directly overhead / underfoot) are the strongest 2-hour windows; minor periods (rising/setting) produce shorter 1-hour windows.

---

## Composite calculation example

**Conditions:** Cape Town, typical summer afternoon

| Signal | Raw | Weight | Weighted |
|--------|-----|--------|----------|
| Weather: 6kn wind, 15km viz | 9.5 | × 0.30 | 2.85 |
| Marine: 0.7m swell, 13s period, 19°C SST | 8.0 | × 0.30 | 2.40 |
| Tides: Slack water | 9.0 | × 0.15 | 1.35 |
| Solunar: Full moon, 45min to major period | 8.5 | × 0.25 | 2.13 |
| **Composite** | | | **8.73 → GO** |

---

## Edge cases

| Situation | Handling |
|-----------|----------|
| Marine API unavailable (GPS in landlocked area) | MarineService throws, ScoreService uses neutral marine score (5.0) |
| No GPS + no saved location | Default coord (San Diego) used for calculation |
| Cached data > 30 min old | Force-refresh on next `refresh()` call |
| Solunar: circumpolar location (always-up/always-down moon) | `riseSet()` returns `nil`; minor periods omitted, major periods from transit only |
| Wave height 0 (open-water API glitch) | Score stays at 10 — treated as flat calm |

---

## Tuning notes

Score weights are defined in `Constants.Weights` and can be adjusted in one place:

```swift
// Constants.swift
enum Weights {
    static let weather: Double = 0.30
    static let marine:  Double = 0.30
    static let tides:   Double = 0.15
    static let solunar: Double = 0.25
}
```

The penalty breakpoints in `ScoreService` are designed for recreational spearfishing at typical reef/rock dives. They are intentionally conservative — a 7/10 score means "a good diver can have a good session", not "professional only".
