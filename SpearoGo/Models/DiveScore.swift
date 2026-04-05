import SwiftUI

enum Verdict: String {
    case go      = "GO"
    case maybe   = "MAYBE"
    case sketchy = "SKETCHY"
    case noGo    = "NO GO"

    var color: Color {
        switch self {
        case .go:      return Color(hex: Constants.Colors.Verdict.go)
        case .maybe:   return Color(hex: Constants.Colors.Verdict.maybe)
        case .sketchy: return Color(hex: Constants.Colors.Verdict.sketchy)
        case .noGo:    return Color(hex: Constants.Colors.Verdict.noGo)
        }
    }
}

struct DiveScore {
    let composite: Double     // 0–10
    let weatherScore: Double  // 0–10
    let marineScore: Double   // 0–10
    let tideScore: Double     // 0–10
    let solunarScore: Double  // 0–10

    var verdict: Verdict {
        switch composite {
        case 8...: return .go
        case 6..<8: return .maybe
        case 4..<6: return .sketchy
        default:   return .noGo
        }
    }

    // Weighted composite: Weather 30%, Marine 30%, Tides 15%, Solunar 25%
    static func calculate(weather: Double, marine: Double, tides: Double, solunar: Double) -> DiveScore {
        let composite = (weather * Constants.Weights.weather)
                      + (marine  * Constants.Weights.marine)
                      + (tides   * Constants.Weights.tides)
                      + (solunar * Constants.Weights.solunar)
        return DiveScore(
            composite: (composite * 10).rounded() / 10,
            weatherScore: weather,
            marineScore: marine,
            tideScore: tides,
            solunarScore: solunar
        )
    }
}
