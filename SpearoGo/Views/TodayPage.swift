import SwiftUI

/// Today's weather, over two screenfuls scrolled vertically.
///
/// Everything fitted on one screen only by being clipped: the header ran under
/// the status area and the daylight row fell off the bottom of the display.
/// Each half is now sized to the viewport, so a crown turn lands squarely on a
/// complete screen rather than halfway through a row.
///
/// Every value is optional and rendered as "—" when the API does not report it.
/// Nothing on this page is substituted or estimated.
struct TodayPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                nowScreen
                    .containerRelativeFrame(.vertical)

                if appState.weatherData != nil {
                    skyScreen
                        .containerRelativeFrame(.vertical)
                }
            }
        }
        .brandPage()
    }

    // MARK: - Screen 1 — what it is doing right now

    private var nowScreen: some View {
        VStack(spacing: Brand.Spacing.item) {
            Text("Today")
                .brandSectionHeader()

            if let weather = appState.weatherData {
                if let condition = weather.conditionLabel {
                    Text(condition).captionStyle()
                }

                Text(weather.airTemp.map { String(format: "%.0f°C", $0) } ?? "—")
                    .verdictStyle(color: Brand.Colors.textPrimary)
                    .accessibilityLabel(temperatureLabel(weather))

                HStack(spacing: 20) {
                    ConditionItem(icon: "thermometer.high",
                                  label: "High",
                                  value: weather.tempMax.map { String(format: "%.0f", $0) } ?? "—",
                                  unit: weather.tempMax == nil ? "" : "°")
                    ConditionItem(icon: "thermometer.low",
                                  label: "Low",
                                  value: weather.tempMin.map { String(format: "%.0f", $0) } ?? "—",
                                  unit: weather.tempMin == nil ? "" : "°")
                }
            } else if appState.isLoading {
                SkeletonBlock(width: 100, height: 28)
                SkeletonBlock(width: 130, height: 20)
                    .accessibilityLabel("Loading today's weather")
            } else {
                Text("No weather data for this spot")
                    .captionStyle()
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, Brand.Spacing.page)
    }

    // MARK: - Screen 2 — rain, cloud, and daylight

    private var skyScreen: some View {
        VStack(spacing: Brand.Spacing.section) {
            Text("Sky")
                .brandSectionHeader()

            if let weather = appState.weatherData {
                HStack(spacing: 20) {
                    ConditionItem(icon: "umbrella",
                                  label: "Rain",
                                  value: weather.precipitationChance.map(String.init) ?? "—",
                                  unit: weather.precipitationChance == nil ? "" : "%")
                    ConditionItem(icon: "cloud",
                                  label: "Cloud",
                                  value: weather.cloudCover.map(String.init) ?? "—",
                                  unit: weather.cloudCover == nil ? "" : "%")
                }
            }

            if let solunar = appState.solunarData,
               let sunrise = solunar.sunrise, let sunset = solunar.sunset {
                Text("☀️ \(timeString(sunrise))   🌙 \(timeString(sunset))")
                    .captionStyle()
                    .lineLimit(1)
                    .accessibilityLabel("Sunrise \(timeString(sunrise)), sunset \(timeString(sunset))")
            } else {
                // The Sun genuinely does not rise or set on some days at high
                // latitude. Say so rather than leaving a blank.
                Text("No sunrise or sunset today")
                    .captionStyle()
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, Brand.Spacing.page)
    }

    private func timeString(_ date: Date) -> String {
        let f = DateFormatter()
        f.timeStyle = .short
        f.dateStyle = .none
        return f.string(from: date)
    }

    private func temperatureLabel(_ weather: WeatherData) -> String {
        guard let temp = weather.airTemp else { return "Air temperature not reported" }
        return String(format: "Air temperature %.0f degrees celsius", temp)
    }
}

#Preview {
    TodayPage()
        .previewAsWatch()
        .environment(AppState.preview(verdict: .go))
}
