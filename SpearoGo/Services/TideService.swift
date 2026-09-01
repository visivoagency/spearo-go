import Foundation
import CoreLocation

/// Real tide predictions, from the `tidesGo` backend.
///
/// The backend owns the whole decision — NOAA where it reaches, WorldTides
/// everywhere else — so this type contains no station lists and no tide maths.
/// That is deliberate: Spearo Go has two clients, and the previous
/// implementation held the same defect in both because the logic was written
/// twice. See spearo-go/functions/tides-go.js.
///
/// There is no synthetic fallback. When there is nothing to show, this returns
/// nil and the UI says so.
/// Why there are no tides, when there are none. A coordinate with no sea is a
/// settled fact; a failed lookup is not, and the two must not read alike.
enum TideLookup {
    case data(TideData)
    case noCoverage
    case unavailable
}

struct TideService {
    private let store = TideStore()

    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 30
        return URLSession(configuration: config)
    }()

    /// Today's tides for a location.
    ///
    /// Order of resort: cached day, then a network fetch covering a week, then
    /// a stale cached day flagged as such, then nil.
    func fetch(coordinate: CLLocationCoordinate2D) async -> TideLookup {
        let today = Self.dayKey(for: Date(), utcOffsetSeconds: 0)

        if let cached = store.fresh(coordinate: coordinate, date: today) {
            return .data(cached)
        }
        if store.knownWithoutCoverage(coordinate: coordinate) {
            return .noCoverage
        }

        do {
            let response = try await request(coordinate: coordinate)

            guard response.available == true, let days = response.days, !days.isEmpty else {
                // A definite "no sea here" is remembered so the watch stops
                // asking; a transient failure is not, and falls through below.
                if response.reason == "outside_coverage" {
                    store.rememberNoCoverage(coordinate: coordinate)
                    return .noCoverage
                }
                return .unavailable
            }

            let offset = response.utcOffsetSeconds ?? 0
            let parsed: [TideData] = days.compactMap { day in
                guard let date = day.date else { return nil }
                return TideData(
                    date: date,
                    events: (day.extremes ?? []).compactMap { e in
                        // 0m is a real chart-datum height, so a missing one
                        // must be dropped rather than floored into a plausible
                        // reading.
                        guard let t = e.t, let height = e.height else { return nil }
                        return TideEvent(timeSeconds: t,
                                         type: e.type?.lowercased() == "high" ? .high : .low,
                                         height: height)
                    },
                    heights: (day.heights ?? []).compactMap { h in
                        guard let t = h.t, let height = h.height else { return nil }
                        return TideHeight(timeSeconds: t, height: height)
                    },
                    stationName: response.station,
                    provenance: response.provenance,
                    utcOffsetSeconds: offset,
                    tidalRange: day.tidalRange ?? "Normal"
                )
            }

            // A week costs one credit, so every day is kept and the next six
            // are free.
            store.save(coordinate: coordinate, days: parsed)
            // Read back through the store so the cache and the network path
            // return exactly the same thing, including the next-day rollover.
            let localToday = Self.dayKey(for: Date(), utcOffsetSeconds: offset)
            guard let today = store.fresh(coordinate: coordinate, date: localToday)
                    ?? parsed.first.flatMap({ store.fresh(coordinate: coordinate, date: $0.date) })
            else { return .unavailable }
            return .data(today)
        } catch {
            // Deliberately not cached as unavailable: an outage must not stick.
            guard var stale = store.stale(coordinate: coordinate, date: today) else {
                return .unavailable
            }
            stale.isStale = true
            return .data(stale)
        }
    }

    private func request(coordinate: CLLocationCoordinate2D) async throws -> TidesGoResponse {
        guard let url = URL(string: Constants.API.tidesBase) else { throw ServiceError.invalidURL }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: [
            "lat": coordinate.latitude,
            "lon": coordinate.longitude,
            "days": 7,
        ])

        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw ServiceError.badResponse }
        // 4xx carries a reason worth reading — "outside_coverage" is an answer,
        // not a failure. Only 5xx and transport errors are treated as faults.
        guard http.statusCode < 500 else { throw ServiceError.badResponse }
        return try JSONDecoder().decode(TidesGoResponse.self, from: data)
    }

    static func dayKey(for date: Date, utcOffsetSeconds: Int) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone(secondsFromGMT: 0)
        return f.string(from: date.addingTimeInterval(TimeInterval(utcOffsetSeconds)))
    }
}

// MARK: - Response shapes

private struct TidesGoResponse: Decodable {
    let available: Bool?
    let reason: String?
    let source: String?
    let station: String?
    let provenance: String?
    let utcOffsetSeconds: Int?
    let days: [TidesGoDay]?
}

private struct TidesGoDay: Decodable {
    let date: String?
    let tidalRange: String?
    let extremes: [TidesGoExtreme]?
    let heights: [TidesGoHeight]?
}

private struct TidesGoExtreme: Decodable {
    let t: Int?
    let type: String?
    let height: Double?
}

private struct TidesGoHeight: Decodable {
    let t: Int?
    let height: Double?
}
