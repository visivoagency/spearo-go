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

    /// Chosen once per refresh, not per render. These are drawn at random from
    /// a pool, and calling that from the view body re-rolled the line on every
    /// recomposition — the verdict copy visibly reshuffled while standing still.
    /// This coordinate has no sea: neither marine nor tide data covers it.
    var hasNoSea: Bool = false

    var personalityMessage: String = ""
    var loadingMessage: String = PersonalityCopy.loading()
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
        loadingMessage = PersonalityCopy.loading()
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

            // No marine data is reported as no marine data. The previous
            // neutral defaults (0m swell, 22°C) were not neutral — they are
            // near-ideal inputs, so a failed lookup INFLATED the verdict.
            //
            // "No sea at this coordinate" and "the lookup failed" are tracked
            // apart, because only the first means this is not a dive spot.
            var marineData: MarineData?
            var marineHasNoSea = false
            if let cached = await cache.cachedMarine(for: coord) {
                marineData = cached
            } else {
                do {
                    let fetched = try await marine.fetch(coordinate: coord)
                    marineData = fetched
                    await cache.store(marine: fetched, for: coord)
                } catch ServiceError.noMarineCoverage {
                    marineHasNoSea = true
                } catch {
                    marineData = nil
                }
            }

            // Real predictions, or a reason there are none.
            let tideLookup  = await tides.fetch(coordinate: coord)
            let tideData: TideData? = {
                if case .data(let d) = tideLookup { return d }
                return nil
            }()

            // Neither the marine model nor any tide station covers this
            // coordinate: it is not water. Saying GO here, from wind and moon
            // alone, reads as a recommendation to dive 400km inland.
            let noSea: Bool = {
                if case .noCoverage = tideLookup { return marineHasNoSea }
                return false
            }()
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
            self.hasNoSea     = noSea
            self.personalityMessage = PersonalityCopy.message(for: score.verdict)
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
