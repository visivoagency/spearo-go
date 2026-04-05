import SwiftUI

struct FishActivityPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        ZStack {
            Color(hex: Constants.Colors.background).ignoresSafeArea()

            VStack(spacing: 10) {
                Text("FISH ACTIVITY")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                    .kerning(1.5)

                if let solunar = appState.solunarData {
                    VStack(spacing: 8) {
                        HStack(spacing: 16) {
                            ConditionItem(
                                icon: moonIcon(phase: solunar.moonPhase),
                                label: "MOON",
                                value: String(format: "%.0f%%", solunar.moonIllumination * 100),
                                unit: ""
                            )
                            ConditionItem(
                                icon: "fish.fill",
                                label: "RATING",
                                value: solunar.activityRating,
                                unit: ""
                            )
                        }

                        if let major = solunar.nextMajorPeriod {
                            SolunarPeriodRow(label: "MAJOR", time: major, color: Constants.Colors.Verdict.go)
                        }
                        if let minor = solunar.nextMinorPeriod {
                            SolunarPeriodRow(label: "MINOR", time: minor, color: Constants.Colors.Verdict.maybe)
                        }
                    }
                } else {
                    ProgressView()
                        .tint(Color(hex: Constants.Colors.primaryAccent))
                }
            }
            .padding()
        }
    }

    private func moonIcon(phase: Double) -> String {
        switch phase {
        case 0..<0.125:  return "moon.fill"
        case 0.125..<0.25: return "moon.stars.fill"
        case 0.25..<0.375: return "moon.circle"
        case 0.375..<0.625: return "circle.fill"
        case 0.625..<0.75: return "moon.circle.fill"
        case 0.75..<0.875: return "moon.fill"
        default:           return "moon.zzz.fill"
        }
    }
}

struct SolunarPeriodRow: View {
    let label: String
    let time: Date
    let color: String

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 8, weight: .semibold))
                .foregroundStyle(Color(hex: color))
                .kerning(1)
                .frame(width: 42, alignment: .leading)
            Text(time, style: .time)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color(hex: Constants.Colors.textPrimary))
            Spacer()
            Image(systemName: "circle.fill")
                .font(.system(size: 5))
                .foregroundStyle(Color(hex: color))
        }
        .padding(.horizontal, 12)
    }
}
