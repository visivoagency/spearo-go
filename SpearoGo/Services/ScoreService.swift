import Foundation

struct ScoreService {
    // MARK: - Public interface

    func score(weather: WeatherData, marine: MarineData, tide: TideData, solunar: SolunarData) -> DiveScore {
        let w = weatherScore(weather)
        let m = marineScore(marine)
        let t = tideScore(tide)
        let s = solunarScore(solunar)
        return DiveScore.calculate(weather: w, marine: m, tides: t, solunar: s)
    }

    // MARK: - Component scores (0–10)

    private func weatherScore(_ d: WeatherData) -> Double {
        var score: Double = 10
        // Wind penalty
        switch d.windSpeed {
        case 0..<10: break
        case 10..<15: score -= 1
        case 15..<20: score -= 3
        case 20..<25: score -= 5
        default:      score -= 8
        }
        // Gust penalty
        if d.windGusts > d.windSpeed + 10 { score -= 1 }
        // Visibility bonus/penalty
        if d.visibility < 5  { score -= 2 }
        if d.visibility > 15 { score += 0.5 }
        return max(0, min(10, score))
    }

    private func marineScore(_ d: MarineData) -> Double {
        var score: Double = 10
        // Wave height penalty
        switch d.waveHeight {
        case 0..<0.5: break
        case 0.5..<1: score -= 1
        case 1..<1.5: score -= 2.5
        case 1.5..<2: score -= 4
        case 2..<2.5: score -= 6
        default:      score -= 9
        }
        // Long-period swell is more manageable
        if d.wavePeriod > 14 { score += 0.5 }
        if d.wavePeriod < 6  { score -= 1 }
        // Water temp: optimal 18–28°C
        if d.seaSurfaceTemp < 12 { score -= 1 }
        if d.seaSurfaceTemp > 30 { score -= 0.5 }
        return max(0, min(10, score))
    }

    private func tideScore(_ d: TideData) -> Double {
        // Slack water and incoming tide are generally better for viz & fish movement
        switch d.phase {
        case "Slack":   return 9
        case "Flood":   return 7.5
        case "Ebb":     return 6
        default:        return 7
        }
    }

    private func solunarScore(_ d: SolunarData) -> Double {
        var score: Double = 5
        // Moon phase bonus
        let phaseFromNewOrFull = abs(d.moonPhase - 0.5) * 2  // 0 = full, 1 = quarter
        score += (1 - phaseFromNewOrFull) * 3  // up to +3 at full/new moon

        // Proximity to major period
        if let major = d.nextMajorPeriod {
            let mins = abs(major.timeIntervalSinceNow) / 60
            if mins < 30        { score += 2.5 }
            else if mins < 60   { score += 1.5 }
            else if mins < 120  { score += 0.5 }
        }

        // Proximity to minor period
        if let minor = d.nextMinorPeriod {
            let mins = abs(minor.timeIntervalSinceNow) / 60
            if mins < 30 { score += 0.5 }
        }

        return max(0, min(10, score))
    }
}
