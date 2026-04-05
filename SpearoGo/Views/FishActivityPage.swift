import SwiftUI

struct FishActivityPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        VStack(spacing: Brand.Spacing.section) {
            Text("Fish Activity")
                .brandSectionHeader()

            if let sol = appState.solunarData {
                Grid(alignment: .center, horizontalSpacing: 20, verticalSpacing: Brand.Spacing.section) {
                    GridRow {
                        ConditionItem(icon: moonIcon(sol.moonPhase),
                                      label: "Moon",
                                      value: String(format: "%.0f%%", sol.moonIllumination * 100),
                                      unit: "")
                        ConditionItem(icon: "fish.fill",
                                      label: "Rating",
                                      value: sol.activityRating,
                                      unit: "")
                    }
                }

                VStack(spacing: Brand.Spacing.micro) {
                    if let major = sol.nextMajorPeriod {
                        SolunarPeriodRow(label: "Major", time: major, color: Brand.Colors.go)
                    }
                    if let minor = sol.nextMinorPeriod {
                        SolunarPeriodRow(label: "Minor", time: minor, color: Brand.Colors.maybe)
                    }
                }
            } else {
                ProgressView().tint(Brand.Colors.primary)
            }
        }
        .padding(Brand.Spacing.page)
        .brandPage()
    }

    private func moonIcon(_ phase: Double) -> String {
        switch phase {
        case 0..<0.1, 0.9...: return "moonphase.new.moon"
        case 0.1..<0.25:      return "moonphase.waxing.crescent"
        case 0.25..<0.35:     return "moonphase.first.quarter"
        case 0.35..<0.5:      return "moonphase.waxing.gibbous"
        case 0.5..<0.6:       return "moonphase.full.moon"
        case 0.6..<0.75:      return "moonphase.waning.gibbous"
        case 0.75..<0.9:      return "moonphase.last.quarter"
        default:              return "moonphase.waning.crescent"
        }
    }
}

struct SolunarPeriodRow: View {
    let label: String
    let time: Date
    let color: Color

    var body: some View {
        HStack {
            Text(label)
                .font(Brand.Typography.itemLabel)
                .kerning(Brand.Kerning.itemLabel)
                .foregroundStyle(color)
                .frame(width: 40, alignment: .leading)

            Text(time, style: .time)
                .font(Brand.Typography.periodTime)
                .foregroundStyle(Brand.Colors.textPrimary)

            Spacer()

            Circle()
                .fill(color)
                .frame(width: 5, height: 5)
        }
        .padding(.horizontal, Brand.Spacing.page)
        .padding(.vertical, 4)
        .background(Brand.Colors.textPrimary.opacity(Brand.Opacity.cardFill))
        .clipShape(RoundedRectangle(cornerRadius: Brand.Radius.chip))
    }
}

// MARK: - Previews

#Preview {
    FishActivityPage()
        .previewAsWatch()
        .environment(AppState.preview())
}
