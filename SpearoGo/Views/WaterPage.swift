import SwiftUI

struct WaterPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        VStack(spacing: Brand.Spacing.section) {
            Text("Water")
                .brandSectionHeader()

            if let marine = appState.marineData {
                Grid(alignment: .center, horizontalSpacing: 20, verticalSpacing: Brand.Spacing.section) {
                    GridRow {
                        ConditionItem(icon: "thermometer.medium",
                                      label: "Temp",
                                      value: String(format: "%.0f", marine.seaSurfaceTemp),
                                      unit: "°C")
                        ConditionItem(icon: "eye",
                                      label: "Viz",
                                      value: vizLabel(marine.waveHeight),
                                      unit: "")
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(waterAccessibilityLabel(marine: marine))

                Text(wetsuitTip(marine.seaSurfaceTemp))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Brand.Spacing.page)
                    .infoPill()
                    .accessibilityLabel("Wetsuit tip: \(wetsuitTip(marine.seaSurfaceTemp))")
            } else {
                // Shimmer skeleton
                Grid(alignment: .center, horizontalSpacing: 20, verticalSpacing: Brand.Spacing.section) {
                    GridRow {
                        ConditionItemSkeleton()
                        ConditionItemSkeleton()
                    }
                }
                SkeletonBlock(width: 120, height: 24)
                    .accessibilityLabel("Loading water conditions")
            }
        }
        .padding(Brand.Spacing.page)
        .brandPage()
    }

    private func vizLabel(_ waveHeight: Double) -> String {
        switch waveHeight {
        case ..<0.5: return "Great"
        case 0.5..<1: return "Good"
        case 1..<1.5: return "Fair"
        default:     return "Poor"
        }
    }

    private func wetsuitTip(_ temp: Double) -> String {
        switch temp {
        case ..<15: return "Cold. 7mm + gloves."
        case 15..<20: return "Chilly. 5mm wetsuit."
        case 20..<25: return "Comfortable. 3mm."
        default:      return "Warm. 1–2mm or skin."
        }
    }

    private func waterAccessibilityLabel(marine: MarineData) -> String {
        let temp = String(format: "Water temperature %.0f degrees celsius", marine.seaSurfaceTemp)
        let viz = "Visibility \(vizLabel(marine.waveHeight))"
        return "\(temp). \(viz)"
    }
}

// MARK: - Previews

#Preview {
    WaterPage()
        .previewAsWatch()
        .environment(AppState.preview())
}

#Preview("Loading") {
    WaterPage()
        .previewAsWatch()
        .environment(AppState.previewLoading())
}
