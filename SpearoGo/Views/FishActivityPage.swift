import SwiftUI

struct FishActivityPage: View {
    @Environment(AppState.self) private var appState
    @State private var crownOffset: Double = 0

    var body: some View {
        ScrollView {
            VStack(spacing: Brand.Spacing.section) {
                Text("Fish Activity")
                    .brandSectionHeader()

                if let sol = appState.solunarData {
                    let moon = moonInfo(sol.moonPhase)
                    Grid(alignment: .center, horizontalSpacing: 20, verticalSpacing: Brand.Spacing.section) {
                        GridRow {
                            ConditionItem(icon: moon.icon,
                                          label: "Moon",
                                          value: String(format: "%.0f%%", sol.moonIllumination * 100),
                                          unit: "")
                            ConditionItem(icon: "fish.fill",
                                          label: "Rating",
                                          value: sol.activityRating,
                                          unit: "")
                        }
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(moonAccessibilityLabel(sol))

                    VStack(spacing: Brand.Spacing.micro) {
                        if let major = sol.nextMajorPeriod {
                            SolunarPeriodRow(label: "Major", time: major, color: Brand.Colors.go)
                        }
                        if let minor = sol.nextMinorPeriod {
                            SolunarPeriodRow(label: "Minor", time: minor, color: Brand.Colors.maybe)
                        }
                    }
                } else {
                    // Shimmer skeleton
                    Grid(alignment: .center, horizontalSpacing: 20, verticalSpacing: Brand.Spacing.section) {
                        GridRow {
                            ConditionItemSkeleton()
                            ConditionItemSkeleton()
                        }
                    }
                    VStack(spacing: Brand.Spacing.micro) {
                        SkeletonBlock(width: 140, height: 26)
                        SkeletonBlock(width: 140, height: 26)
                    }
                    .accessibilityLabel("Loading fish activity data")
                }
            }
            .padding(Brand.Spacing.page)
        }
        .focusable()
        .digitalCrownRotation($crownOffset)
        .brandPage()
    }

    private func moonInfo(_ phase: Double) -> (icon: String, name: String) {
        switch phase {
        case 0..<0.1, 0.9...: return ("moonphase.new.moon",          "New Moon")
        case 0.1..<0.25:      return ("moonphase.waxing.crescent",   "Waxing Crescent")
        case 0.25..<0.35:     return ("moonphase.first.quarter",     "First Quarter")
        case 0.35..<0.5:      return ("moonphase.waxing.gibbous",    "Waxing Gibbous")
        case 0.5..<0.6:       return ("moonphase.full.moon",         "Full Moon")
        case 0.6..<0.75:      return ("moonphase.waning.gibbous",    "Waning Gibbous")
        case 0.75..<0.9:      return ("moonphase.last.quarter",      "Last Quarter")
        default:              return ("moonphase.waning.crescent",   "Waning Crescent")
        }
    }

    private func moonAccessibilityLabel(_ sol: SolunarData) -> String {
        let phase = moonInfo(sol.moonPhase).name
        let illum = String(format: "%.0f percent illumination", sol.moonIllumination * 100)
        return "\(phase), \(illum). Activity rating: \(sol.activityRating)"
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
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label) period")
    }
}

// MARK: - Previews

#Preview {
    FishActivityPage()
        .previewAsWatch()
        .environment(AppState.preview())
}

#Preview("Loading") {
    FishActivityPage()
        .previewAsWatch()
        .environment(AppState.previewLoading())
}
