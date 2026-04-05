import SwiftUI

struct ConditionsPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        VStack(spacing: Brand.Spacing.section) {
            Text("Conditions")
                .brandSectionHeader()

            if let weather = appState.weatherData, let marine = appState.marineData {
                Grid(alignment: .center, horizontalSpacing: 20, verticalSpacing: Brand.Spacing.section) {
                    GridRow {
                        ConditionItem(icon: "wind",
                                      label: "Wind",
                                      value: String(format: "%.0f", weather.windSpeed),
                                      unit: "kn")
                        ConditionItem(icon: "water.waves",
                                      label: "Swell",
                                      value: String(format: "%.1f", marine.waveHeight),
                                      unit: "m")
                    }
                    GridRow {
                        ConditionItem(icon: "arrow.up.right",
                                      label: "Dir",
                                      value: compassDirection(weather.windDirection),
                                      unit: "")
                        ConditionItem(icon: "timer",
                                      label: "Period",
                                      value: String(format: "%.0f", marine.wavePeriod),
                                      unit: "s")
                    }
                }
            } else {
                ProgressView().tint(Brand.Colors.primary)
            }
        }
        .padding(Brand.Spacing.page)
        .brandPage()
    }

    private func compassDirection(_ degrees: Double) -> String {
        let dirs = ["N","NE","E","SE","S","SW","W","NW"]
        return dirs[Int((degrees + 22.5) / 45.0) % 8]
    }
}

struct ConditionItem: View {
    let icon: String
    let label: String
    let value: String
    let unit: String

    var body: some View {
        VStack(spacing: Brand.Spacing.micro) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundStyle(Brand.Colors.primary)

            Text(label)
                .itemLabelStyle()

            HStack(alignment: .lastTextBaseline, spacing: 1) {
                Text(value)
                    .dataValueStyle()
                if !unit.isEmpty {
                    Text(unit).unitStyle()
                }
            }
        }
        .frame(minWidth: 60)
    }
}

// MARK: - Previews

#Preview {
    ConditionsPage()
        .previewAsWatch()
        .environment(AppState.preview())
}
