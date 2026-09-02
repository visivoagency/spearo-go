import Foundation
import CoreLocation

/// Persistent store for tide predictions.
///
/// The app's CacheService is deliberately memory-only — "watch storage is
/// precious" — but a week of tides has to survive a relaunch, or every cold
/// start costs a metered lookup. The backend returns seven days for the price
/// of one, so keeping them is most of the point.
///
/// Small on disk: at most 7 days across a handful of saved spots, and anything
/// whose day has passed is dropped on the next write.
struct TideStore {
    private let defaults = UserDefaults.standard
    private static let prefix = "tides."
    /// Predictions for a given day do not change.
    private static let cacheDuration: TimeInterval = 24 * 3600

    private struct Entry: Codable {
        let savedAt: Date
        var days: [TideData] = []
        /// The backend said this coordinate has no sea. A settled fact.
        var noCoverage: Bool = false
    }

    private func key(_ coordinate: CLLocationCoordinate2D) -> String {
        String(format: "%@%.2f,%.2f", Self.prefix, coordinate.latitude, coordinate.longitude)
    }

    private func read(_ coordinate: CLLocationCoordinate2D) -> Entry? {
        guard let data = defaults.data(forKey: key(coordinate)) else { return nil }
        return try? JSONDecoder().decode(Entry.self, from: data)
    }

    private func write(_ entry: Entry, for coordinate: CLLocationCoordinate2D) {
        guard let data = try? JSONEncoder().encode(entry) else { return }
        defaults.set(data, forKey: key(coordinate))
    }

    /// A cached day, still within its life.
    func fresh(coordinate: CLLocationCoordinate2D, date: String) -> TideData? {
        guard let entry = read(coordinate), !entry.noCoverage,
              Date().timeIntervalSince(entry.savedAt) < Self.cacheDuration else { return nil }
        return rollover(entry.days, on: date)
    }

    /// A cached day past its life, for when the network is gone. Better than
    /// nothing, and flagged in the UI — but never invented.
    func stale(coordinate: CLLocationCoordinate2D, date: String) -> TideData? {
        guard let entry = read(coordinate), !entry.noCoverage else { return nil }
        return rollover(entry.days, on: date)
    }

    /// Whether this coordinate is already known to have no sea.
    func knownWithoutCoverage(coordinate: CLLocationCoordinate2D) -> Bool {
        guard let entry = read(coordinate) else { return false }
        return entry.noCoverage && Date().timeIntervalSince(entry.savedAt) < Self.cacheDuration
    }

    func save(coordinate: CLLocationCoordinate2D, days: [TideData]) {
        let today = TideService.dayKey(for: Date(), utcOffsetSeconds: 0)
        let kept = days.filter { $0.date >= today }
        write(Entry(savedAt: Date(), days: kept), for: coordinate)
    }

    /// Remembered so a landlocked spot stops asking. Held for the same day as
    /// real data — long enough to stop the traffic, short enough that a backend
    /// gaining coverage is picked up.
    func rememberNoCoverage(coordinate: CLLocationCoordinate2D) {
        write(Entry(savedAt: Date(), days: [], noCoverage: true), for: coordinate)
    }
}

/// A day, carrying the following day's turns alongside its own.
///
/// Without this the page reads "—" for the next high from the day's last high
/// until midnight — exactly when tomorrow's dive gets planned. It lives beside
/// the store because both the network and the cache path come through it;
/// applying it in only one of them is how it was missed on Wear.
func rollover(_ days: [TideData], on date: String) -> TideData? {
    guard let index = days.firstIndex(where: { $0.date == date }) else { return nil }
    var day = days[index]
    let next = days.indices.contains(index + 1) ? days[index + 1].events : []
    day.events = day.events + next
    return day
}
