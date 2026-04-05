import Foundation

struct SolunarData {
    let moonPhase: Double         // 0.0 (new) – 1.0 (full cycle)
    let moonIllumination: Double  // 0.0 – 1.0
    let moonrise: Date?
    let moonset: Date?
    let sunrise: Date?
    let sunset: Date?
    let nextMajorPeriod: Date?    // ~2h window of peak activity
    let nextMinorPeriod: Date?    // ~1h window of minor activity
    let activityRating: String    // "Excellent", "Good", "Fair", "Poor"
    let fetchedAt: Date
}
