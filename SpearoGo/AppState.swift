import Foundation
import CoreLocation
import WidgetKit
#if os(watchOS)
import WatchKit
#endif

@MainActor @Observable
final class AppState {
    // ── Published state ──────────────────────────────────────────────────────
    var weatherData:  WeatherData?
    var marineData:   MarineData?
    var tideData:     TideData?
    var solunarData:  SolunarData?
    var diveScore:    DiveScore?
    var isLoading:    Bool = false
    var error:        Error?

    /// Tracks when data was last successfully refreshed.
    var lastRefreshed: Date?

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

    // Default fallback (San Diego, CA) if GPS unavailable and no saved location
    private let defaultCoordinate = CLLocationCoordinate2D(latitude: 32.7, longitude: -117.2)

    var activeCoordinate: CLLocationCoordinate2D {
        activeOverrideCoordinate
            ?? locationService.currentCoordinate
            ?? defaultCoordinate
    }

    /// Formatted relative time since last refresh, e.g. "2 min ago" or "stale".
    var lastRefreshedLabel: String? {
        guard let lastRefreshed else { return nil }
        let elapsed = Date().timeIntervalSince(lastRefreshed)
        if elapsed < 60 { return "Just now" }
        let minutes = Int(elapsed / 60)
        if minutes < 60 { return "\(minutes) min ago" }
        return "Stale"
    }

    /// True when neither a saved location nor live GPS is available,
    /// meaning conditions are for the San Diego fallback coordinates.
    var isUsingFallbackLocation: Bool {
        activeOverrideCoordinate == nil && locationService.currentCoordinate == nil
    }

    /// True if cached data is older than 30 minutes.
    var isStale: Bool {
        guard let lastRefreshed else { return false }
        return Date().timeIntervalSince(lastRefreshed) > 1800
    }

    // ── Refresh pipeline ──────────────────────────────────────────────────────
    func refresh() async {
        isLoading = true
        error = nil
        locationService.requestLocation()

        let previousVerdict = diveScore?.verdict

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
            } else if let fetched = try? await marine.fetch(coordinate: coord) {
                marineData = fetched
                await cache.store(marine: marineData, for: coord)
            } else {
                // Marine API can fail for landlocked coordinates (HTTP 400)
                // or transient network issues — use neutral defaults so the
                // app still produces a score from weather/tides/solunar.
                marineData = MarineData(
                    waveHeight: 0, wavePeriod: 10,
                    waveDirection: 0, seaSurfaceTemp: 22,
                    fetchedAt: Date()
                )
            }

            let tideData    = tides.calculate(coordinate: coord)
            let solunarData = solunar.calculate(coordinate: coord)
            let score       = scorer.score(weather: weatherData,
                                           marine:  marineData,
                                           tide:    tideData,
                                           solunar: solunarData)

            self.weatherData  = weatherData
            self.marineData   = marineData
            self.tideData     = tideData
            self.solunarData  = solunarData
            self.diveScore    = score
            self.lastRefreshed = Date()
            self.isLoading    = false

            // Push latest score to widget via shared UserDefaults
            SharedScore(
                composite: score.composite,
                verdict: score.verdict.rawValue,
                updatedAt: Date()
            ).save()
            WidgetCenter.shared.reloadAllTimelines()

            // Haptic feedback on verdict change
            #if os(watchOS)
            if let prev = previousVerdict, prev != score.verdict {
                playVerdictHaptic(score.verdict)
            } else if previousVerdict == nil {
                WKInterfaceDevice.current().play(.click)
            }
            #endif
        } catch {
            self.error     = error
            self.isLoading = false
            #if os(watchOS)
            WKInterfaceDevice.current().play(.failure)
            #endif
        }
    }

    // ── Haptics ───────────────────────────────────────────────────────────────

    #if os(watchOS)
    private func playVerdictHaptic(_ verdict: Verdict) {
        let device = WKInterfaceDevice.current()
        switch verdict {
        case .go:
            device.play(.success)
        case .maybe:
            device.play(.click)
        case .sketchy:
            device.play(.directionUp)
        case .noGo:
            device.play(.failure)
        }
    }
    #endif
}
