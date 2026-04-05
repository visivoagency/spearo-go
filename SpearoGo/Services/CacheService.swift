import Foundation
import CoreLocation

// In-memory cache with TTL. Watch storage is precious so we keep only latest fetch.
actor CacheService {
    private struct Entry<T> {
        let value: T
        let expiresAt: Date
    }

    private var weatherCache: [String: Entry<WeatherData>] = [:]
    private var marineCache:  [String: Entry<MarineData>]  = [:]

    private let ttl: TimeInterval = 1800  // 30 minutes

    func cachedWeather(for coordinate: CLLocationCoordinate2D) -> WeatherData? {
        guard let entry = weatherCache[key(coordinate)], entry.expiresAt > Date() else { return nil }
        return entry.value
    }

    func cachedMarine(for coordinate: CLLocationCoordinate2D) -> MarineData? {
        guard let entry = marineCache[key(coordinate)], entry.expiresAt > Date() else { return nil }
        return entry.value
    }

    func store(weather: WeatherData, for coordinate: CLLocationCoordinate2D) {
        weatherCache[key(coordinate)] = Entry(value: weather, expiresAt: Date().addingTimeInterval(ttl))
    }

    func store(marine: MarineData, for coordinate: CLLocationCoordinate2D) {
        marineCache[key(coordinate)] = Entry(value: marine, expiresAt: Date().addingTimeInterval(ttl))
    }

    private func key(_ coord: CLLocationCoordinate2D) -> String {
        // Round to 2 decimal places (~1km grid) for cache hit rate
        String(format: "%.2f,%.2f", coord.latitude, coord.longitude)
    }
}
