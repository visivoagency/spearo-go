import SwiftUI

struct WaterPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        ZStack {
            Color(hex: Constants.Colors.background).ignoresSafeArea()

            VStack(spacing: 10) {
                Text("WATER")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                    .kerning(1.5)

                if let marine = appState.marineData {
                    HStack(spacing: 20) {
                        ConditionItem(
                            icon: "thermometer.medium",
                            label: "TEMP",
                            value: String(format: "%.0f", marine.seaSurfaceTemp),
                            unit: "°C"
                        )
                        ConditionItem(
                            icon: "eye",
                            label: "VIZ",
                            value: visibilityLabel(temp: marine.seaSurfaceTemp,
                                                   waveHeight: marine.waveHeight),
                            unit: ""
                        )
                    }

                    Text(tempComment(temp: marine.seaSurfaceTemp))
                        .font(.caption2)
                        .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
                } else {
                    ProgressView()
                        .tint(Color(hex: Constants.Colors.primaryAccent))
                }
            }
            .padding()
        }
    }

    // Estimated visibility based on temp + swell proxy
    private func visibilityLabel(temp: Double, waveHeight: Double) -> String {
        if waveHeight > 2.0 { return "Poor" }
        if waveHeight > 1.0 { return "Fair" }
        if temp > 22 { return "Good" }
        return "OK"
    }

    private func tempComment(temp: Double) -> String {
        switch temp {
        case ..<15: return "Cold. 7mm + gloves."
        case 15..<20: return "Chilly. 5mm wetsuit."
        case 20..<25: return "Comfortable. 3mm."
        default:     return "Warm. 1-2mm or skin."
        }
    }
}
