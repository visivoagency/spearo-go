import Foundation
import CoreLocation

@Observable
final class AppState {
    // ── Published state ──────────────────────────────────────────────────────
    var weatherData:  WeatherData?
    var marineData:   MarineData?
    var tideData:     TideData?
    var solunarData:  SolunarData?
    var diveScore:    DiveScore?
    var isLoading:    Bool = false
    var error:        Error?

    /// Set by ContentView when the user activates a saved location.
    /// Nil means "use live GPS".
    var activeOverrideCoordinate: CLLocationCoordinate2D?

    // ── Services ─────────────────────────────────────────────────────────────
    let locationService  = LocationService()
    private let weather  = WeatherService()
    private let marine   = MarineService()
    private let tides    = TideService()
    private let solunar  = SolunarService()
    private let scorer   = ScoreService()
    private let cache    = CacheService()

    // Default fallback if GPS unavailable and no saved location
    private let defaultCoordinate = CLLocationCoordinate2D(latitude: 32.7, longitude: -117.2)

    var activeCoordinate: CLLocationCoordinate2D {
        activeOverrideCoordinate
            ?? locationService.currentCoordinate
            ?? defaultCoordinate
    }

    // ── Refresh pipeline ──────────────────────────────────────────────────────
    func refresh() async {
        isLoading = true
        error = nil
        locationService.requestLocation()

        do {
            let coord = activeCoordinate

            let weatherData: WeatherData
            if let cached = await cache.cachedWeather(for: coord) {
                weatherData = cached
            } else {
                weatherData = try await weather.fetch(coordinate: coord)
                await cache.store(weather: weatherData, for: coord)
            }

            let marineData: MarineData
            if let cached = await cache.cachedMarine(for: coord) {
                marineData = cached
            } else {
                marineData = try await marine.fetch(coordinate: coord)
                await cache.store(marine: marineData, for: coord)
            }

            let tideData    = tides.calculate(coordinate: coord)
            let solunarData = solunar.calculate(coordinate: coord)
            let score       = scorer.score(weather: weatherData,
                                           marine:  marineData,
                                           tide:    tideData,
                                           solunar: solunarData)

            await MainActor.run {
                self.weatherData  = weatherData
                self.marineData   = marineData
                self.tideData     = tideData
                self.solunarData  = solunarData
                self.diveScore    = score
                self.isLoading    = false
            }
        } catch {
            await MainActor.run {
                self.error     = error
                self.isLoading = false
            }
        }
    }
}
