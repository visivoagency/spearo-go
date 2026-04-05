import SwiftUI

struct ConditionsPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        ZStack {
            Color(hex: Constants.Colors.background).ignoresSafeArea()

            VStack(spacing: 10) {
                Text("CONDITIONS")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                    .kerning(1.5)

                if let weather = appState.weatherData, let marine = appState.marineData {
                    HStack(spacing: 20) {
                        ConditionItem(
                            icon: "wind",
                            label: "WIND",
                            value: String(format: "%.0f", weather.windSpeed),
                            unit: "kn"
                        )
                        ConditionItem(
                            icon: "water.waves",
                            label: "SWELL",
                            value: String(format: "%.1f", marine.waveHeight),
                            unit: "m"
                        )
                    }

                    HStack(spacing: 20) {
                        ConditionItem(
                            icon: "arrow.up.right",
                            label: "DIRECTION",
                            value: compassDirection(degrees: weather.windDirection),
                            unit: ""
                        )
                        ConditionItem(
                            icon: "timer",
                            label: "PERIOD",
                            value: String(format: "%.0f", marine.wavePeriod),
                            unit: "s"
                        )
                    }
                } else {
                    ProgressView()
                        .tint(Color(hex: Constants.Colors.primaryAccent))
                }
            }
            .padding()
        }
    }

    private func compassDirection(degrees: Double) -> String {
        let directions = ["N","NE","E","SE","S","SW","W","NW"]
        let index = Int((degrees + 22.5) / 45.0) % 8
        return directions[index]
    }
}

struct ConditionItem: View {
    let icon: String
    let label: String
    let value: String
    let unit: String

    var body: some View {
        VStack(spacing: 2) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundStyle(Color(hex: Constants.Colors.primaryAccent))
            Text(label)
                .font(.system(size: 8, weight: .medium))
                .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                .kerning(0.8)
            HStack(alignment: .lastTextBaseline, spacing: 1) {
                Text(value)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color(hex: Constants.Colors.textPrimary))
                if !unit.isEmpty {
                    Text(unit)
                        .font(.system(size: 9))
                        .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                }
            }
        }
        .frame(minWidth: 55)
    }
}
