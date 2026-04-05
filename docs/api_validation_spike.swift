#!/usr/bin/env swift
// API Validation Spike — run with: swift docs/api_validation_spike.swift
// Validates Open-Meteo weather + marine APIs for 6 global coordinates,
// then runs synthetic tide + solunar calculations for each.
// No API keys required.

import Foundation

// ---------------------------------------------------------------------------
// Test coordinates
// ---------------------------------------------------------------------------

let locations: [(name: String, lat: Double, lon: Double)] = [
    ("San Diego",   32.7,  -117.2),
    ("Sydney",     -33.9,   151.2),
    ("Marseille",   43.3,     5.4),
    ("Bali",        -8.7,   115.2),
    ("Cape Town",  -34.0,    18.5),
    ("Cancun",      21.2,   -86.8),
]

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

func fetch(_ urlString: String) async throws -> Data {
    guard let url = URL(string: urlString) else { throw URLError(.badURL) }
    let (data, response) = try await URLSession.shared.data(from: url)
    guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
    guard (200..<300).contains(http.statusCode) else {
        throw URLError(.init(rawValue: http.statusCode))
    }
    return data
}

func weatherURL(lat: Double, lon: Double) -> String {
    "https://api.open-meteo.com/v1/forecast"
    + "?latitude=\(lat)&longitude=\(lon)"
    + "&current=wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility"
    + "&wind_speed_unit=ms&timezone=auto&forecast_days=1"
}

func marineURL(lat: Double, lon: Double) -> String {
    "https://marine-api.open-meteo.com/v1/marine"
    + "?latitude=\(lat)&longitude=\(lon)"
    + "&current=wave_height,wave_period,wave_direction,sea_surface_temperature"
    + "&timezone=auto"
}

// ---------------------------------------------------------------------------
// Synthetic tide calculator (mirrors TideService.swift)
// ---------------------------------------------------------------------------

func tideHeight(lat: Double, lon: Double, t: Double) -> Double {
    let m2Period: Double = 44712
    let s2Period: Double = 43200
    let lunarCycle: Double = 2551443
    let lunarEpoch: Double = 947182440

    let lonOffset = (lon / 360.0) * m2Period
    let lunarPhase = (t - lunarEpoch).truncatingRemainder(dividingBy: lunarCycle) / lunarCycle
    let springScale = 0.6 + 0.4 * cos(2 * .pi * 2 * abs(lunarPhase - 0.5) - .pi)

    let m2 = cos(2 * .pi * (t + lonOffset) / m2Period)
    let s2 = 0.35 * cos(2 * .pi * (t + lonOffset) / s2Period)
    return (m2 + s2) * springScale
}

func syntheticTide(name: String, lat: Double, lon: Double) {
    let now = Date().timeIntervalSince1970
    let h = tideHeight(lat: lat, lon: lon, t: now)
    let isRising = tideHeight(lat: lat, lon: lon, t: now + 1800) > h
    let scaled = (h + 1) * 1.5

    print("  [Tide]    height=\(String(format: "%.2f", scaled))m  \(isRising ? "↑ Flood" : "↓ Ebb")")
}

// ---------------------------------------------------------------------------
// Solunar calculator (mirrors SolunarService.swift)
// ---------------------------------------------------------------------------

func julianDay(_ date: Date) -> Double {
    date.timeIntervalSince1970 / 86400.0 + 2440587.5
}

func moonPhase(jd: Double) -> Double {
    let D = jd - 2451545.0
    let raw = (D / 29.53058868).truncatingRemainder(dividingBy: 1)
    return raw < 0 ? raw + 1 : raw
}

func moonIllumination(jd: Double) -> Double {
    let D = jd - 2451545.0
    let L0 = (218.316 + 13.176396 * D).truncatingRemainder(dividingBy: 360)
    let M  = (134.963 + 13.064993 * D).truncatingRemainder(dividingBy: 360)
    let F  = (93.272  + 13.229350 * D).truncatingRemainder(dividingBy: 360)
    let moonLon = L0 + 6.289 * sin(M * .pi / 180)
    let g  = (357.529 + 0.98560028 * D).truncatingRemainder(dividingBy: 360)
    let q  = (280.459 + 0.98564736 * D).truncatingRemainder(dividingBy: 360)
    let sunLon = q + 1.915 * sin(g * .pi / 180)
    let angle = abs(moonLon - sunLon).truncatingRemainder(dividingBy: 360)
    return (1 - cos(angle * .pi / 180)) / 2
}

func solunarRating(jd: Double) -> String {
    let phase = moonPhase(jd: jd)
    let score = cos(2 * .pi * phase) * 0.5 + 0.5
    switch score {
    case 0.75...: return "Excellent"
    case 0.55...: return "Good"
    case 0.35...: return "Fair"
    default:      return "Poor"
    }
}

// ---------------------------------------------------------------------------
// Main validation loop
// ---------------------------------------------------------------------------

print("\n========================================")
print("  Spearo Go — API Validation Spike")
print("  \(Date())")
print("========================================\n")

let jd = julianDay(Date())
let globalMoonPhase = moonPhase(jd: jd)
let globalIllum     = moonIllumination(jd: jd)
let globalRating    = solunarRating(jd: jd)

print("Global solunar (date-invariant by location):")
print("  Moon phase:        \(String(format: "%.3f", globalMoonPhase)) (0=new, 0.5=full)")
print("  Moon illumination: \(String(format: "%.1f%%", globalIllum * 100))")
print("  Activity rating:   \(globalRating)\n")

await withTaskGroup(of: Void.self) { group in
    for loc in locations {
        group.addTask {
            print("--- \(loc.name) (\(loc.lat), \(loc.lon)) ---")
            do {
                // Weather
                let wData = try await fetch(weatherURL(lat: loc.lat, lon: loc.lon))
                if let json = try? JSONSerialization.jsonObject(with: wData) as? [String: Any],
                   let current = json["current"] as? [String: Any] {
                    let windMs  = current["wind_speed_10m"]  as? Double ?? 0
                    let windDir = current["wind_direction_10m"] as? Double ?? 0
                    let gustMs  = current["wind_gusts_10m"]  as? Double ?? 0
                    print("  [Weather] wind=\(String(format: "%.1f", windMs * 1.94384))kn  dir=\(Int(windDir))°  gusts=\(String(format: "%.1f", gustMs * 1.94384))kn  OK")
                } else {
                    print("  [Weather] parse error")
                }
            } catch {
                print("  [Weather] FAILED: \(error.localizedDescription)")
            }

            do {
                // Marine
                let mData = try await fetch(marineURL(lat: loc.lat, lon: loc.lon))
                if let json = try? JSONSerialization.jsonObject(with: mData) as? [String: Any],
                   let current = json["current"] as? [String: Any] {
                    let waveH   = current["wave_height"]             as? Double ?? 0
                    let wavePer = current["wave_period"]             as? Double ?? 0
                    let sst     = current["sea_surface_temperature"] as? Double ?? 0
                    print("  [Marine]  wave=\(String(format: "%.2f", waveH))m  period=\(String(format: "%.0f", wavePer))s  SST=\(String(format: "%.1f", sst))°C  OK")
                } else {
                    print("  [Marine]  parse error")
                }
            } catch {
                print("  [Marine]  FAILED: \(error.localizedDescription)")
            }

            // Synthetic tide (no network)
            syntheticTide(name: loc.name, lat: loc.lat, lon: loc.lon)

            // Solunar (no network — already computed globally above)
            print("  [Solunar] rating=\(globalRating)  illum=\(String(format: "%.1f%%", globalIllum * 100))  OK")
            print("")
        }
    }
}

print("========================================")
print("  Validation complete.")
print("========================================\n")
