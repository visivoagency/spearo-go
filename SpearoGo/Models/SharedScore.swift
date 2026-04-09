import Foundation

// ─────────────────────────────────────────────────────────────────────────────
// SharedScore.swift — Lightweight Codable struct for app → widget data transfer
// via UserDefaults (App Group suite).
//
// The main app writes after each refresh; the widget reads at timeline reload.
// ─────────────────────────────────────────────────────────────────────────────

struct SharedScore: Codable {
    let composite: Double
    let verdict: String       // Verdict.rawValue ("GO", "MAYBE", etc.)
    let updatedAt: Date

    /// App Group suite name — must match the entitlements on both targets.
    static let suiteName = "group.agency.visivo.SpearoGo"

    /// UserDefaults key.
    static let key = "latestDiveScore"

    // MARK: - Write (called by main app)

    func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        UserDefaults(suiteName: Self.suiteName)?.set(data, forKey: Self.key)
    }

    // MARK: - Read (called by widget)

    static func load() -> SharedScore? {
        guard let data = UserDefaults(suiteName: Self.suiteName)?.data(forKey: Self.key),
              let score = try? JSONDecoder().decode(SharedScore.self, from: data)
        else { return nil }
        return score
    }

    // MARK: - Convenience

    var verdictEnum: Verdict {
        Verdict(rawValue: verdict) ?? .noGo
    }

    /// Sample data for widget previews.
    static let preview = SharedScore(
        composite: 7.3,
        verdict: "MAYBE",
        updatedAt: Date().addingTimeInterval(-300)
    )
}
