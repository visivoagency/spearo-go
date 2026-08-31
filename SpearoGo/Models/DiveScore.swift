import SwiftUI

enum Verdict: String {
    case go      = "GO"
    case maybe   = "MAYBE"
    case sketchy = "SKETCHY"
    case noGo    = "NO GO"

    var color: Color {
        Brand.Colors.forVerdict(self)
    }
}

struct DiveScore {
    let composite: Double      // 0–10
    let weatherScore: Double   // 0–10
    let marineScore: Double?   // 0–10, nil when the location has no marine data
    let tideScore: Double?     // 0–10, nil when tides are unavailable
    let solunarScore: Double   // 0–10

    /// Signals that could not be measured, for the UI to name.
    var missingSignals: [String] {
        var missing: [String] = []
        if marineScore == nil { missing.append("swell") }
        if tideScore == nil { missing.append("tides") }
        return missing
    }

    var isPartial: Bool { !missingSignals.isEmpty }

    var verdict: Verdict {
        switch composite {
        case 8...: return .go
        case 6..<8: return .maybe
        case 4..<6: return .sketchy
        default:   return .noGo
        }
    }

    // Weighted composite: Weather 30%, Marine 30%, Tides 15%, Solunar 25%.
    //
    // A missing signal is dropped and the remaining weights renormalised,
    // rather than being scored as an average one. Substituting a placeholder
    // would let absent data move the verdict — which is exactly what a 0m,
    // 22°C "neutral" marine default used to do, inflating the score for any
    // location the marine API does not cover.
    static func calculate(weather: Double, marine: Double?, tides: Double?, solunar: Double) -> DiveScore {
        var weighted: Double = 0
        var totalWeight: Double = 0

        func include(_ value: Double?, weight: Double) {
            guard let value else { return }
            weighted += value * weight
            totalWeight += weight
        }

        include(weather, weight: Constants.Weights.weather)
        include(marine,  weight: Constants.Weights.marine)
        include(tides,   weight: Constants.Weights.tides)
        include(solunar, weight: Constants.Weights.solunar)

        let composite = totalWeight > 0 ? weighted / totalWeight : 0

        return DiveScore(
            composite: (composite * 10).rounded() / 10,
            weatherScore: weather,
            marineScore: marine,
            tideScore: tides,
            solunarScore: solunar
        )
    }
}
