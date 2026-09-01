import Foundation

enum TidePhase: String, Codable {
    case slack = "Slack"
    case flood = "Flood"
    case ebb   = "Ebb"
}

enum TideType: String, Codable {
    case high, low
}

struct TideEvent: Codable, Equatable {
    let timeSeconds: Int   // epoch seconds, UTC
    let type: TideType
    let height: Double     // metres above chart datum

    var date: Date { Date(timeIntervalSince1970: TimeInterval(timeSeconds)) }
}

struct TideHeight: Codable, Equatable {
    let timeSeconds: Int
    let height: Double
}

/// A day of real tide predictions from the `tidesGo` backend.
///
/// Holds only what the server sent. Everything time-dependent — which tide is
/// next, whether the water is rising, the height right now — is derived here at
/// read time, because a week of this is cached and "next" changes by the minute.
///
/// There is no synthetic fallback. The previous implementation invented a curve
/// anchored to the Unix epoch and got Lagos, Portugal exactly inverted: it
/// showed a low at 04:49 where the gauge reads a high at 04:50. Wrong tide times
/// are worse than none, because a diver cannot tell them apart.
struct TideData: Codable, Equatable {
    let date: String                 // yyyy-MM-dd, station-local
    var events: [TideEvent] = []
    var heights: [TideHeight] = []
    var stationName: String?
    /// "gauge" for a real tide station, "model" for the ocean grid estimate.
    var provenance: String?
    var utcOffsetSeconds: Int = 0
    var tidalRange: String = "Normal"
    /// Served from an expired cache because the lookup failed.
    var isStale: Bool = false
    var fetchedAt: Date = Date()

    /// A grid estimate is materially weaker in the inlets these users dive.
    var isModelEstimate: Bool { provenance == "model" }

    /// The moment to render, shifted into the tide station's own local time.
    ///
    /// Format the result in UTC. A diver in Germany reading Portuguese tides
    /// must see the time the Portuguese tide table prints, not that instant
    /// translated into German wall clock. The Wear app was showing 00:50 for a
    /// low that happens at 23:50 in Lagos before this existed.
    func stationLocalDate(_ event: TideEvent) -> Date {
        Date(timeIntervalSince1970: TimeInterval(event.timeSeconds + utcOffsetSeconds))
    }

    // Derived state takes the moment as a parameter so it can be tested. A week
    // of predictions is cached and "next" changes by the minute, so none of this
    // can be precomputed server-side.

    func nextEvent(at now: Date = Date()) -> TideEvent? {
        let t = Int(now.timeIntervalSince1970)
        return events.first { $0.timeSeconds > t }
    }

    func nextHigh(at now: Date = Date()) -> TideEvent? {
        let t = Int(now.timeIntervalSince1970)
        return events.first { $0.timeSeconds > t && $0.type == .high }
    }

    func nextLow(at now: Date = Date()) -> TideEvent? {
        let t = Int(now.timeIntervalSince1970)
        return events.first { $0.timeSeconds > t && $0.type == .low }
    }

    /// Rising when the next turn is a high.
    func isRising(at now: Date = Date()) -> Bool { nextEvent(at: now)?.type == .high }

    /// Linear interpolation between the two hourly readings either side of now.
    func currentHeight(at now: Date = Date()) -> Double? {
        let t = Int(now.timeIntervalSince1970)
        guard heights.count > 1 else { return nil }
        for i in 0..<(heights.count - 1) {
            let a = heights[i], b = heights[i + 1]
            if a.timeSeconds <= t && b.timeSeconds > t {
                let span = Double(b.timeSeconds - a.timeSeconds)
                guard span > 0 else { return a.height }
                return a.height + (b.height - a.height) * (Double(t - a.timeSeconds) / span)
            }
        }
        return nil
    }

    /// Slack water is the half hour either side of a turn, when the flow stills
    /// — the window divers actually care about. Previously derived from a
    /// fabricated curve's amplitude; now measured against real turn times.
    func phase(at now: Date = Date()) -> TidePhase {
        let t = Int(now.timeIntervalSince1970)
        let nearest = events.min { abs($0.timeSeconds - t) < abs($1.timeSeconds - t) }
        if let nearest, abs(nearest.timeSeconds - t) / 60 <= 30 { return .slack }
        return isRising(at: now) ? .flood : .ebb
    }
}
