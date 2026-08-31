# Spearo Go — real tide data

**Date:** 2026-08-31
**Status:** Design approved, implementation not started
**Trigger:** Customer report — Galaxy Watch Ultra 2, Lagos, Portugal. App showed
next low ~04:49 and next high ~11:00, neither matching the Instituto
Hidrográfico table for Lagos.

---

## 1. The defect

`SpearoGo/Services/TideService.swift` and
`wear/app/src/main/java/com/spearotracker/spearogo/services/TideService.kt` are
the same algorithm in two languages. Both are described in comments as a
"simplified harmonic model" with "±30–45 min" accuracy. Neither claim is true.

```swift
let m2 = cos(2 * .pi * (time + lonOffset) / m2Period)
let s2 = 0.35 * cos(2 * .pi * (time + lonOffset) / s2Period)
```

`time` is `Date().timeIntervalSince1970`. There is no astronomical argument, no
epoch correction, and no phase constant. The model therefore asserts that high
water occurs at **00:00 UTC on 1 January 1970** at longitude 0, and everywhere
else at that instant shifted by `longitude / 360 × 12.42h`.

Consequences, in order of severity:

1. **The phase is arbitrary.** The reference instant is a Unix implementation
   detail, not an astronomical event. Any agreement with a real tide table at
   any location is coincidence.
2. **Latitude is never read.** The parameter is accepted and discarded. Two
   spots on the same meridian 3,000 km apart are given identical tides.
3. **The lunar term does not drive phase.** `lunarPhase` feeds only
   `springScale`, an amplitude multiplier. The moon's actual position — the one
   thing that sets when high water happens — is absent from the timing maths
   entirely.
4. **The heights are invented.** `scale(h) = (h + 1) * 1.5` maps the normalised
   curve onto a fixed 0–3 m range regardless of location. Lagos runs about
   3.5 m at springs; the Mediterranean runs under 0.4 m. Both are rendered as
   0–3 m to one decimal place, which reads as a measurement.
5. **Nothing distinguishes it from real data in the UI.** `TidesPage` renders
   these numbers with the same formatting and confidence as a gauge reading.

The customer's diagnosis — that the app needs location-specific constants and
more constituents — is correct about the symptom but understates the cause.
Adding K1 and O1 to this would not help, because the M2 term it would join is
not anchored to the moon.

### Why this is not a display bug

Portugal is UTC+1 in August. A correct computation rendered in the wrong
timezone would be off by exactly one hour. The reported times are off by hours
in a way that does not correspond to any offset, which is consistent with an
arbitrary phase rather than a formatting error.

### Prior art — Spearo Vision hit this exact defect

Vision shipped the same class of bug and fixed it in 1.0.26 after a customer in
Galicia was shown a 14:55 high tide against a real 12:38. See
`spearo-vision/docs/CUSTOMER-REPLY-alejandro-tides.md`. The header comment on
`spearo-vision/lib/services/tide_service.dart:14` is a written instruction not
to reintroduce a fabricating fallback:

> It used to: a lunar approximation that never read the latitude or longitude it
> was passed, so every location on earth got identical tides, rendered
> indistinguishably from a real reading. [...] Do not reintroduce a fallback
> that fabricates.

Go is still shipping the thing that warning is about.

---

## 2. Decisions taken

| Decision | Choice | Rejected |
|---|---|---|
| Data source | Real predictions, honest gaps — mirror Vision | Better offline harmonic engine; NOAA-only |
| Backend access | New public HTTPS function, no Firebase SDK in Go | Firebase SDK + anonymous auth; relaxing Vision's `tidesProxy` |
| Missing-tide scoring | Redistribute the 15%, flag the verdict as partial | Neutral placeholder score; blocking the verdict |
| Scope | Both platforms, one release | Wear OS first; single-platform spike |
| Audit width | Tides, plus a read of solunar and marine | Tides only; fix-everything |

---

## 3. Architecture

```
watchOS (URLSession)  ─┐
                       ├─→  tidesGo  ─┬─→ NOAA CO-OPS   (US, free, no key)
Wear OS  (OkHttp)     ─┘   (onRequest) └─→ WorldTides    (global, keyed, billed)
                                            └─→ neither answers → available:false
```

### 3.1 Why the source choice moves server-side

Vision decides NOAA-vs-WorldTides in the client and carries a NOAA station list
in Dart. Copying that into Swift *and* Kotlin means two station lists, two
haversine implementations, and two chances to diverge — which is precisely how
Go's two `TideService` files came to contain the same defect twice.

`tidesGo` owns the entire decision. The clients send a coordinate and a date and
receive tide events. They contain no knowledge of NOAA, WorldTides, station
distances, or datums. This is the main structural departure from Vision and it
is deliberate.

### 3.2 The function

New `exports.tidesGo` in `spearo-vision/functions/index.js`, alongside
`tidesProxy`. Separate from `tidesProxy`, not a modification of it: Vision's
function requires an authenticated caller and Go has no accounts, and keeping
them separate gives per-app cost attribution and an independent kill switch.

**Deployment is by name.** The Firebase project is shared with three other apps:

```
firebase deploy --only functions:tidesGo
```

Never a bare `firebase deploy`, and never `--force` on indexes.

**Request** — `POST`, JSON body:

| Field | Type | Rules |
|---|---|---|
| `lat` | number | finite, −90..90, required |
| `lon` | number | finite, −180..180, required |
| `date` | string | `YYYY-MM-DD`, optional; rejected if malformed, never defaulted |
| `days` | number | clamped 1..7, default 7 |

**Response**, available:

```json
{
  "available": true,
  "source": "noaa" | "worldtides",
  "station": "Lagos" | null,
  "stationDistanceKm": 4.2,
  "provenance": "gauge" | "model",
  "datum": "MLLW" | "CD",
  "utcOffsetSeconds": 3600,
  "extremes": [{ "t": 1756612140, "type": "high", "height": 3.1 }],
  "heights":  [{ "t": 1756612800, "height": 2.4 }]
}
```

**Response**, unavailable:

```json
{ "available": false, "reason": "outside_coverage" | "lookup_failed" | "budget_exceeded" }
```

**Parameters carried over from `tidesProxy` unchanged**, because each encodes a
bug already paid for once:

- `stationDistance=50` (km). The WorldTides account default is 10, which its
  dashboard labels degrees (~1,100 km) — far enough that a Lagos query could be
  answered by a gauge in Biscay.
- `datum=CD` for WorldTides, `datum=MLLW` for NOAA. WorldTides defaults to mean
  sea level, which renders low tides negative. "−0.76 m" reads as broken to a
  diver judging whether a spot is divable, and CD is what printed tables use.
- `&extremes&heights&localtime`, `days` up to 7 per call. WorldTides bills one
  credit per seven days of extremes, so a week costs exactly what a day costs.

**Protection:**

- App Check (App Attest on watchOS, Play Integrity on Wear OS).
- Per-IP rate limit.
- A hard daily WorldTides credit ceiling in Firestore. On breach the function
  returns `available: false, reason: "budget_exceeded"` rather than spending.
  Go is free; an open endpoint against a metered API needs a floor under it.
- Coordinates rounded to 2 dp before the upstream call, so the cache key and the
  billed request share a ~1 km grid.

### 3.3 Time handling

`t` is a true UTC epoch in seconds. `utcOffsetSeconds` is the offset **at the
tide station**.

Clients render `t + utcOffsetSeconds` as station-local wall clock, not device
local time. For a diver standing at the spot these are the same; for a saved
spot in another timezone the station-local reading is the one that matches the
printed table, which is the number the customer is comparing against.

This is the same hazard Vision documents at `tide_service.dart` in
`_parseWorldTidesTime`: WorldTides returns station-local times carrying an
offset, and letting a date parser normalise them to UTC re-creates the
wrong-time defect two layers below where it was fixed.

### 3.4 Client `TideService`, both platforms

The harmonic model is **deleted**, not retained as a fallback. Order of resort:

1. Persistent cache for the requested day.
2. Network fetch — one call covering seven days; every day in the response is
   written to the cache, so the following six are free.
3. Stale cache, if present, marked stale in the UI.
4. `TideData.unavailable(reason:)`.

`available: false` and network failures are **not** cached, so a transient
outage does not persist as "unavailable" for the whole TTL after it recovers.

### 3.5 Persistence — new work, no Vision equivalent

`SpearoGo/Services/CacheService.swift` and its Kotlin twin are in-memory only,
with a 30-minute TTL and a comment that watch storage is precious. A week-ahead
cache that survives a launch cannot live there.

- **watchOS:** a new `TideStore` backed by SwiftData, which the app already uses
  for saved locations. One record per (rounded coordinate, day).
- **Wear OS:** a new Room entity and DAO, alongside the existing `AppDatabase`.

Bounded: at most 7 days × 5 saved spots, evicting anything whose day has passed.
Roughly 40 rows and a few KB.

### 3.6 Models

`TideData` gains, in both languages:

| Field | Purpose |
|---|---|
| `isUnavailable` / `unavailableReason` | Mirrors `spearo-vision/lib/models/tide_data.dart:36` |
| `stationName` | Named gauge, or null |
| `provenance` | `gauge` or `model` — never rendered identically |
| `isStale` | Served from an expired cache |
| `utcOffsetSeconds` | Station-local rendering |

`provenance` exists because WorldTides falls back to an ocean grid model that is
materially weaker in estuaries and inlets — exactly the water this app's users
dive. Presenting a grid estimate and a gauge reading identically is a milder
version of the defect being fixed.

### 3.7 Score

`ScoreService.tideScore` currently returns 6–9 for every possible input and can
never abstain, so an absent tide would score as an average one and silently move
the verdict.

`DiveScore.calculate` takes an availability flag per signal and renormalises over
the signals that are present. With tides missing, 30/30/25 becomes 35.3/35.3/29.4
(that is, each weight divided by 0.85).

The Verdict page marks a renormalised score as partial: *"Tides unavailable —
verdict from wind, swell and fish activity."* The score stays useful and its
basis stays visible.

---

## 4. Testing

The acceptance test is the customer's own case.

**Fixture:** Lagos, Portugal — 37.10 N, 8.67 W — on a fixed date, asserted
against the published Instituto Hidrográfico table.

| Test | Assertion |
|---|---|
| Lagos golden fixture | Every extreme within ±10 min and ±0.2 m of the published table |
| Same fixture, both languages | Swift and Kotlin produce identical output from identical JSON |
| Outside coverage | `available:false` → `isUnavailable`, no times rendered |
| Network failure, warm cache | Cached day served, marked stale |
| Network failure, cold cache | Unavailable — and specifically no invented times |
| Failure not cached | Two calls after a transient failure both attempt the network |
| Station-local rendering | An event at 13:52+01:00 renders 13:52, not 12:52 |
| Score renormalisation | Tides absent → weights sum to 1.0 over the remaining three |
| Score, all present | Composite unchanged from today's behaviour |
| Budget ceiling | Over budget → `available:false`, no upstream call |

The golden fixture is committed as JSON and shared by both test suites. If Swift
and Kotlin can drift, they will — that is the history of this file.

---

## 5. Also has to change

**Store listings.** Both currently advertise the defect:

- `README.md`: "Offline tide and solunar calculations — no API key needed" and
  the score table's "Synthetic lunar harmonic — offline".
- `docs/APP_STORE_METADATA.md` and `docs/GOOGLE_PLAY_METADATA.md` — same claim.

Solunar remains genuinely offline. Tides no longer are. Submitting with the old
copy would ship a claim already known to be false.

**Source comments.** The "±30–45 min vs. real tides" comment goes with the code
it describes. The replacement carries Vision's warning against reintroducing a
fabricating fallback.

---

## 6. Audit findings — solunar and marine

Both were read for the same defect class: fabricated or location-blind data
presented as real.

### SolunarService — sound, one real bug

Genuine Meeus orbital math. Reads both latitude and longitude and uses them
properly, in rise/set, in the transit calculation, and in local sidereal time.
Not in the same class as the tide model.

**Bug — `SpearoGo/Services/SolunarService.swift:39` and
`wear/app/src/main/java/com/spearotracker/spearogo/services/SolunarService.kt:45`:**

```swift
let antiTransit = transit.addingTimeInterval(6 * 3600)
```

Lunar lower culmination falls roughly 12h25m after upper culmination, not 6h.
Anti-transit is placed about six and a half hours early, so one of the two major
solunar periods is wrong, and `nextMajorPeriod` picks the wrong one for much of
the day. It feeds a ±2.5 point swing in `solunarScore`, which is 25% of the
composite.

One-line fix in each language. Folded into this change: same file family, same
release, and it would be strange to correct the tides while leaving this.

### MarineService — real API, fabricating fallback

The fetch itself is a genuine Open-Meteo call with real coordinates. But `SpearoGo/AppState.swift:92`,
and identically
`wear/app/src/main/java/com/spearotracker/spearogo/ui/AppViewModel.kt:137`:

```swift
// Marine API can fail for landlocked coordinates (HTTP 400)
// or transient network issues — use neutral defaults so the
// app still produces a score from weather/tides/solunar.
marineData = MarineData(waveHeight: 0, wavePeriod: 10,
                        waveDirection: 0, seaSurfaceTemp: 22, ...)
```

These are not neutral. `waveHeight: 0` is a flat calm sea and
`seaSurfaceTemp: 22` is comfortable water; both are near-ideal inputs to
`marineScore`, which carries 30% of the composite. A diver whose marine call
fails on a rough day is shown an *inflated* verdict, and the Water page reports
"22 °C" as a reading.

Same defect class as the tides, at twice the weight.

**Not fixed in this change** — the audit brief was to report rather than fix
unless something matched the tides in severity. It is close. But the
availability-flag machinery this spec adds to `DiveScore` is exactly what a
proper fix needs, so this becomes cheap immediately afterwards.

**Recommendation:** next release, mark marine unavailable and renormalise, as
the tides now do. Logged in `docs/BACKLOG.md`.

---

## 7. Open items

Neither blocks implementation; both block the golden fixture and the deploy.

1. **WorldTides credit headroom.** Unknown at time of writing. The daily ceiling
   in §3.2 is specified so the answer changes a config value, not the design.
   Assumed default until confirmed: a conservative cap, with Go degrading to
   `available:false` past it rather than spending without a floor.
2. **Customer's screenshots and Instituto Hidrográfico comparison.** He offered
   both. They become the golden fixture in §4. Until they arrive the fixture is
   built from the published Lagos table directly, and the customer's own day is
   added as a second case when received.

---

## 8. Order of work

1. `tidesGo` function, App Check, rate limit, budget ceiling — deploy by name.
2. Golden fixture JSON from the Lagos table.
3. Shared `TideData` model changes, both languages.
4. Persistence — SwiftData store and Room entity.
5. `TideService` rewrite, both languages; delete the harmonic model.
6. `DiveScore` availability flags and renormalisation, both languages.
7. Verdict and Tides page states — unavailable, stale, grid-model provenance.
8. `antiTransit` fix, both languages.
9. Listing and README copy.
10. Tests green on both platforms against the same fixture, then ship as one
    version to both stores.
