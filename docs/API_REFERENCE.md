# Spearo Go — API Reference

## External APIs

Both APIs are from [Open-Meteo](https://open-meteo.com) — free, no API key, no rate limit for reasonable use, global coverage via ECMWF / ERA5 data.

---

## 1. Open-Meteo Weather API

### Endpoint

```
GET https://api.open-meteo.com/v1/forecast
```

### Parameters used

| Parameter | Value | Notes |
|-----------|-------|-------|
| `latitude` | `{lat}` | Decimal degrees |
| `longitude` | `{lon}` | Decimal degrees |
| `current` | `wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility` | Comma-separated |
| `wind_speed_unit` | `ms` | m/s — converted to knots in-app |
| `timezone` | `auto` | Auto-detect from coordinates |
| `forecast_days` | `1` | Only need current conditions |

### Example request

```
https://api.open-meteo.com/v1/forecast
  ?latitude=32.7
  &longitude=-117.2
  &current=wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility
  &wind_speed_unit=ms
  &timezone=auto
  &forecast_days=1
```

### Response shape

```json
{
  "latitude": 32.75,
  "longitude": -117.25,
  "timezone": "America/Los_Angeles",
  "current_units": {
    "wind_speed_10m": "m/s",
    "wind_direction_10m": "°",
    "wind_gusts_10m": "m/s",
    "cloud_cover": "%",
    "visibility": "m"
  },
  "current": {
    "time": "2026-04-05T12:00",
    "wind_speed_10m": 1.5,
    "wind_direction_10m": 200,
    "wind_gusts_10m": 2.8,
    "cloud_cover": 15,
    "visibility": 24140
  }
}
```

### Swift model

```swift
struct WeatherData {
    let windSpeed: Double       // knots (converted from m/s × 1.94384)
    let windDirection: Double   // degrees
    let windGusts: Double       // knots
    let visibility: Double      // km (converted from m ÷ 1000)
    let cloudCover: Int         // %
    let fetchedAt: Date
}
```

### Error conditions

| HTTP Status | Meaning | App behaviour |
|-------------|---------|---------------|
| 200 | OK | Parse and use |
| 400 | Bad coordinates | `ServiceError.badResponse` |
| 429 | Rate limited | Retry after 60s |
| 5xx | Server error | Use cached data if available |

---

## 2. Open-Meteo Marine API

### Endpoint

```
GET https://marine-api.open-meteo.com/v1/marine
```

### Parameters used

| Parameter | Value | Notes |
|-----------|-------|-------|
| `latitude` | `{lat}` | Decimal degrees |
| `longitude` | `{lon}` | Decimal degrees |
| `current` | `wave_height,wave_period,wave_direction,sea_surface_temperature` | Comma-separated |
| `timezone` | `auto` | Auto-detect |

### Example request

```
https://marine-api.open-meteo.com/v1/marine
  ?latitude=-33.9
  &longitude=151.2
  &current=wave_height,wave_period,wave_direction,sea_surface_temperature
  &timezone=auto
```

### Response shape

```json
{
  "latitude": -33.875,
  "longitude": 151.25,
  "timezone": "Australia/Sydney",
  "current": {
    "time": "2026-04-05T23:00",
    "wave_height": 1.1,
    "wave_period": 6.0,
    "wave_direction": 145.0,
    "sea_surface_temperature": 26.5
  }
}
```

### Swift model

```swift
struct MarineData {
    let waveHeight: Double       // metres (significant wave height)
    let wavePeriod: Double       // seconds (peak period)
    let waveDirection: Double    // degrees (direction waves are coming FROM)
    let seaSurfaceTemp: Double   // °C
    let fetchedAt: Date
}
```

### Coverage note

The marine API uses ERA5 ocean reanalysis + GFS forecasts. Coverage is **global ocean only** — API returns 400 for landlocked coordinates. App falls back to neutral marine score (5.0) in that case.

---

## 3. Synthetic Tide Calculator (offline)

No network call. Pure Swift math.

### Method

Two-constituent harmonic model:
- **M2** — principal lunar semidiurnal (period 12.42h / 44,712s)
- **S2** — principal solar semidiurnal (period 12.00h / 43,200s)

Spring/neap scaling via lunar phase distance from new/full moon.

### Accuracy

| Metric | Value |
|--------|-------|
| High/low timing | ±30–45 minutes vs. official tide tables |
| High/low height | ±20–30cm relative to actual range |
| Phase (flood/ebb/slack) | ±15 minutes |

Sufficient for "dive-window" UX. Not suitable for navigation or anchoring.

### Output

```swift
struct TideData {
    let currentHeight: Double    // metres (relative, normalised 0–3m)
    let isRising: Bool
    let phase: String            // "Flood", "Ebb", "Slack"
    let nextHighTime: Date
    let nextHighHeight: Double   // metres
    let nextLowTime: Date
    let nextLowHeight: Double    // metres
    let fetchedAt: Date
}
```

---

## 4. Solunar Calculator (offline)

No network call. Pure Swift math based on Jean Meeus, *Astronomical Algorithms* (2nd ed., 1998).

### Calculations

| Output | Method |
|--------|--------|
| Moon phase | Synodic period fraction since known new moon (J2000.0 epoch) |
| Moon illumination | Phase angle between moon and sun longitudes |
| Moon position | Low-precision equatorial coords (Meeus Ch.48, ~1° accuracy) |
| Sun position | Low-precision equatorial coords (Meeus Ch.25) |
| Moonrise/moonset | Hour angle formula from declination + latitude |
| Major periods | Moon transit (upper/lower culmination) |
| Minor periods | Moonrise + moonset times |

### Output

```swift
struct SolunarData {
    let moonPhase: Double          // 0.0 (new) – 1.0 (complete cycle)
    let moonIllumination: Double   // 0.0 – 1.0
    let moonrise: Date?
    let moonset: Date?
    let sunrise: Date?
    let sunset: Date?
    let nextMajorPeriod: Date?     // ~2h peak activity window
    let nextMinorPeriod: Date?     // ~1h minor activity window
    let activityRating: String     // "Excellent", "Good", "Fair", "Poor"
    let fetchedAt: Date
}
```

---

## Cache strategy

```
CacheService (actor)
├── weatherCache: [String: Entry<WeatherData>]  — keyed by "lat,lon" (2dp grid)
└── marineCache:  [String: Entry<MarineData>]   — keyed by "lat,lon" (2dp grid)

TTL: 1800s (30 minutes)
Eviction: lazy (check on read)
Storage: in-memory only (no disk persistence beyond TTL)
```

Cache key resolution: coordinates rounded to 2 decimal places (~1.1km grid) — ensures nearby coordinates (e.g., slight GPS drift) hit the same cache entry.

---

## Rate limiting & costs

| API | Daily limit | Cost |
|-----|------------|------|
| Open-Meteo Weather | 10,000 req/day (free tier) | Free |
| Open-Meteo Marine | 10,000 req/day (free tier) | Free |
| Tide calculator | Unlimited | Free (offline) |
| Solunar calculator | Unlimited | Free (offline) |

At 30-minute cache TTL, a single user makes max ~96 API calls/day (2 APIs × 48 refreshes). Well within free tier. Commercial use (>10k users) would require upgrading to Open-Meteo's paid plan (~$50/month for 1M requests).
