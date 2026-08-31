import SwiftUI

/// Today's weather: air temperature, conditions, and daylight.
///
/// Every value here is optional and rendered as "—" when the API does not
/// report it. Nothing on this page is substituted or estimated.
struct TodayPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        VStack(spacing: Brand.Spacing.item) {
            Text("Today")
                .brandSectionHeader()

            if let weather = appState.weatherData {
                if let condition = weather.conditionLabel {
                    Text(condition)
                        .captionStyle()
                }

                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    Text(weather.airTemp.map { String(format: "%.0f", $0) } ?? "—")
                        .dataValueStyle()
                    if weather.airTemp != nil {
                        Text("°C").unitStyle()
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(temperatureLabel(weather))

                Grid(alignment: .center, horizontalSpacing: 20, verticalSpacing: Brand.Spacing.item) {
                    GridRow {
                        ConditionItem(icon: "thermometer.high",
                                      label: "High",
                                      value: weather.tempMax.map { String(format: "%.0f", $0) } ?? "—",
                                      unit: weather.tempMax == nil ? "" : "°")
                        ConditionItem(icon: "thermometer.low",
                                      label: "Low",
                                      value: weather.tempMin.map { String(format: "%.0f", $0) } ?? "—",
                                      unit: weather.tempMin == nil ? "" : "°")
                    }
                    GridRow {
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
                    Text("\(timeString(sunrise))  ·  \(timeString(sunset))")
                        .captionStyle()
                        .accessibilityLabel("Sunrise \(timeString(sunrise)), sunset \(timeString(sunset))")
                }
            } else if appState.isLoading {
                SkeletonBlock(width: 100, height: 28)
                SkeletonBlock(width: 130, height: 20)
                    .accessibilityLabel("Loading today's weather")
            } else {
                Text("No weather data for this spot")
                    .captionStyle()
                    .multilineTextAlignment(.center)
                    .accessibilityLabel("No weather data for this spot")
            }
        }
        .padding(Brand.Spacing.page)
        .brandPage()
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
