import Foundation
import CoreLocation

@Observable
final class AppState {
    var weatherData: WeatherData?
    var marineData: MarineData?
    var tideData: TideData?
    var solunarData: SolunarData?
    var diveScore: DiveScore?
    var isLoading: Bool = false
    var error: Error?

    private let weatherService  = WeatherService()
    private let marineService   = MarineService()
    private let tideService     = TideService()
    private let solunarService  = SolunarService()
    private let scoreService    = ScoreService()
    private let cache           = CacheService()
    let locationService         = LocationService()

    // Default to San Diego until GPS resolves
    private var activeCoordinate: CLLocationCoordinate2D {
        locationService.currentCoordinate
            ?? CLLocationCoordinate2D(latitude: 32.7, longitude: -117.2)
    }

    func refresh() async {
        isLoading = true
        error = nil
        locationService.requestLocation()

        do {
            let coord = activeCoordinate

            // Check cache first
            let weather: WeatherData
            if let cached = await cache.cachedWeather(for: coord) {
                weather = cached
            } else {
                weather = try await weatherService.fetch(coordinate: coord)
                await cache.store(weather: weather, for: coord)
            }

            let marine: MarineData
            if let cached = await cache.cachedMarine(for: coord) {
                marine = cached
            } else {
                marine = try await marineService.fetch(coordinate: coord)
                await cache.store(marine: marine, for: coord)
            }

            // Pure math — no network needed
            let tide    = tideService.calculate(coordinate: coord)
            let solunar = solunarService.calculate(coordinate: coord)
            let score   = scoreService.score(weather: weather, marine: marine,
                                              tide: tide, solunar: solunar)

            await MainActor.run {
                self.weatherData  = weather
                self.marineData   = marine
                self.tideData     = tide
                self.solunarData  = solunar
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
