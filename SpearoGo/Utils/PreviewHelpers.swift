import SwiftUI
import SwiftData

// ─────────────────────────────────────────────────────────────────────────────
// PreviewHelpers.swift — Utilities for consistent, on-brand Xcode Previews.
//
// Every #Preview in Spearo Go should use these helpers so previews always:
//   • Show on a pure-black background
//   • Use the correct watch screen dimensions
//   • Have access to a mock AppState with realistic data
//   • Render in the watch simulator bezel
//
// Usage:
//   #Preview {
//       VerdictPage()
//           .previewAsWatch()
//           .environment(AppState.preview())
//   }
// ─────────────────────────────────────────────────────────────────────────────

// MARK: - Watch preview frame

extension View {
    /// Constrains the preview to the Apple Watch 44mm screen size (176×215pt)
    /// and sets the black background, matching a real watch screen.
    func previewAsWatch(size: WatchSize = .mm44) -> some View {
        self
            .frame(width: size.width, height: size.height)
            .background(Brand.Colors.background)
            .clipShape(RoundedRectangle(cornerRadius: size.cornerRadius))
            .previewDisplayName(size.name)
    }
}

enum WatchSize {
    case mm41, mm44, mm45, mm49

    var width:        CGFloat { switch self { case .mm41: 176; case .mm44: 184; case .mm45: 198; case .mm49: 205 } }
    var height:       CGFloat { switch self { case .mm41: 215; case .mm44: 224; case .mm45: 242; case .mm49: 251 } }
    var cornerRadius: CGFloat { switch self { case .mm41: 42;  case .mm44: 44;  case .mm45: 47;  case .mm49: 49  } }
    var name:         String  { switch self { case .mm41: "41mm"; case .mm44: "44mm"; case .mm45: "45mm"; case .mm49: "49mm Ultra" } }
}

// MARK: - Mock AppState

extension AppState {
    /// Returns a fully-populated AppState for use in Xcode Previews.
    static func preview(verdict: Verdict = .go) -> AppState {
        let state = AppState()
        state.weatherData   = MockData.weather
        state.marineData    = MockData.marine
        state.tideData      = MockData.tide
        state.solunarData   = MockData.solunar
        state.diveScore     = MockData.score(verdict: verdict)
        state.lastRefreshed = Date().addingTimeInterval(-120) // "2 min ago"
        state.isLoading     = false
        return state
    }

    /// AppState stuck in loading — for testing spinner previews.
    static func previewLoading() -> AppState {
        let state = AppState()
        state.isLoading = true
        return state
    }
}

// MARK: - Mock data

enum MockData {
    static let weather = WeatherData(
        windSpeed:     14.0,
        windDirection: 45.0,
        windGusts:     18.0,
        visibility:    18.0,
        cloudCover:    20,
        airTemp:       19.0,
        tempMax:       23.0,
        tempMin:       14.0,
        precipitationChance: 10,
        weatherCode:   2,
        fetchedAt:     Date()
    )

    static let marine = MarineData(
        waveHeight:     1.2,
        wavePeriod:     8.0,
        waveDirection:  135.0,
        seaSurfaceTemp: 22.0,
        fetchedAt:      Date()
    )

    /// The real Lagos, Portugal tide day, from the tidesGo backend on
    /// 2026-08-31 — the spot the customer reported. Times are anchored to
    /// "now" so previews always show a live-looking day.
    static let tide: TideData = {
        let now = Int(Date().timeIntervalSince1970)
        return TideData(
            date: TideService.dayKey(for: Date(), utcOffsetSeconds: 3600),
            events: [
                TideEvent(timeSeconds: now - 3600 * 4, type: .high, height: 3.28),
                TideEvent(timeSeconds: now + 3600 * 2, type: .low, height: 0.69),
                TideEvent(timeSeconds: now + 3600 * 8, type: .high, height: 3.40),
            ],
            heights: [
                TideHeight(timeSeconds: now - 1800, height: 1.9),
                TideHeight(timeSeconds: now + 1800, height: 1.6),
            ],
            stationName: "Lagos",
            provenance: "gauge",
            utcOffsetSeconds: 3600,
            tidalRange: "Spring"
        )
    }()

    static let solunar = SolunarData(
        moonPhase:        0.79,
        moonIllumination: 0.87,
        moonrise:         Date().addingTimeInterval(-3600 * 3),
        moonset:          Date().addingTimeInterval(3600 * 9),
        sunrise:          Date().addingTimeInterval(-3600 * 5),
        sunset:           Date().addingTimeInterval(3600 * 7),
        nextMajorPeriod:  Date().addingTimeInterval(3600 * 1.5),
        nextMinorPeriod:  Date().addingTimeInterval(-3600 * 0.3),
        activityRating:   "Good",
        fetchedAt:        Date()
    )

    static func score(verdict: Verdict) -> DiveScore {
        switch verdict {
        case .go:
            return DiveScore.calculate(weather: 9.5, marine: 8.5, tides: 9.0, solunar: 8.5)
        case .maybe:
            return DiveScore.calculate(weather: 7.0, marine: 6.0, tides: 7.5, solunar: 6.0)
        case .sketchy:
            return DiveScore.calculate(weather: 4.5, marine: 4.0, tides: 6.0, solunar: 5.5)
        case .noGo:
            return DiveScore.calculate(weather: 1.0, marine: 1.5, tides: 6.0, solunar: 4.0)
        }
    }
}

// MARK: - Convenience preview macros

/// Use in any SwiftUI file to quickly preview all 4 verdict states side by side.
struct AllVerdictsPreview: View {
    var body: some View {
        HStack(spacing: 12) {
            ForEach([Verdict.go, .maybe, .sketchy, .noGo], id: \.rawValue) { v in
                VerdictPage()
                    .previewAsWatch()
                    .environment(AppState.preview(verdict: v))
            }
        }
        .padding()
        .background(Color(hex: "#111111"))
    }
}

#Preview("All Verdicts") {
    AllVerdictsPreview()
}
