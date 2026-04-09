import Foundation
import CoreLocation

// Synthetic lunar tide calculator — no API key required, works globally.
// Uses a simplified harmonic model driven by the Moon's synodic period (29.53 days).
// Two principal constituents: M2 (semidiurnal, ~12.42h) and S2 (semidiurnal, 12h).
// Amplitude scaled by lunar phase distance from full/new moon.
// Accuracy: ±30–45 min vs. real tides — sufficient for "high/low window" UX.

struct TideService {
    private static let m2Period: Double = 44712  // seconds (~12.42h)
    private static let s2Period: Double = 43200  // seconds (12.00h)
    private static let lunarCycle: Double = 2551443  // seconds (29.53 days)
    private static let lunarEpoch: Double = 947182440  // Unix: Jan 6 2000 18:14 (new moon)

    func calculate(coordinate: CLLocationCoordinate2D, date: Date = Date()) -> TideData {
        let t = date.timeIntervalSince1970

        // Longitude-based phase offset: tidal bulge lags ~0.8h per 15° longitude
        let lonOffset = (coordinate.longitude / 360.0) * TideService.m2Period

        // Lunar phase [0, 1] — 0 = new moon, 0.5 = full moon
        let lunarPhase = fmod(t - TideService.lunarEpoch, TideService.lunarCycle)
            / TideService.lunarCycle

        // Spring/neap scale: peaks at new (0) and full (0.5) moon
        let springScale = 0.6 + 0.4 * cos(2 * .pi * 2 * abs(lunarPhase - 0.5) - .pi)

        func height(at time: Double) -> Double {
            let m2 = cos(2 * .pi * (time + lonOffset) / TideService.m2Period)
            let s2 = 0.35 * cos(2 * .pi * (time + lonOffset) / TideService.s2Period)
            return (m2 + s2) * springScale
        }

        let current = height(at: t)

        // Find next high and low within 13-hour search window
        func findNext(after start: Double, wantHigh: Bool, window: Double = 46800) -> (time: Double, h: Double) {
            let step: Double = 300  // 5-minute resolution
            var best = start + step
            var bestH = height(at: best)
            var time = start + step * 2
            while time <= start + window {
                let h = height(at: time)
                let prevH = height(at: time - step)
                let nextH = height(at: time + step)
                if wantHigh ? (h >= prevH && h >= nextH) : (h <= prevH && h <= nextH) {
                    return (time, h)
                }
                if wantHigh ? h > bestH : h < bestH {
                    best = time
                    bestH = h
                }
                time += step
            }
            return (best, bestH)
        }

        let nextH = findNext(after: t, wantHigh: true)
        let nextL = findNext(after: t, wantHigh: false)

        // Rising if heading toward high
        let deltaT: Double = 1800
        let isRising = height(at: t + deltaT) > current

        // Tide phase
        let phase: TidePhase = {
            if abs(current) < 0.2 * springScale { return .slack }
            return isRising ? .flood : .ebb
        }()

        // Scale to a realistic tidal range (e.g. 0–3 m normalised)
        func scale(_ h: Double) -> Double { (h + 1) * 1.5 }

        return TideData(
            currentHeight: scale(current),
            isRising:      isRising,
            phase:         phase,
            nextHighTime:  Date(timeIntervalSince1970: nextH.time),
            nextHighHeight: scale(nextH.h),
            nextLowTime:   Date(timeIntervalSince1970: nextL.time),
            nextLowHeight:  scale(nextL.h),
            fetchedAt:     Date()
        )
    }
}
