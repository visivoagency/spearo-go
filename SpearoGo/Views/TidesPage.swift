import SwiftUI

struct TidesPage: View {
    @Environment(AppState.self) private var appState
    @State private var crownOffset: Double = 0

    var body: some View {
        ScrollView {
            VStack(spacing: Brand.Spacing.section) {
                Text("Tides")
                    .brandSectionHeader()

                if let tide = appState.tideData {
                    HStack(spacing: 16) {
                        TideEventView(label: "HIGH",
                                      time: tide.nextHighTime,
                                      height: tide.nextHighHeight)

                        Divider()
                            .frame(height: 44)
                            .background(Brand.Colors.textSecondary.opacity(Brand.Opacity.borderLine))

                        TideEventView(label: "LOW",
                                      time: tide.nextLowTime,
                                      height: tide.nextLowHeight)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(tideTimesLabel(tide))

                    TideDirectionView(isRising: tide.isRising, phase: tide.phase)
                        .brandCard(padding: Brand.Spacing.item)
                        .accessibilityLabel("Tide is \(tide.isRising ? "incoming" : "outgoing"), phase: \(tide.phase.rawValue)")

                    // Current height
                    HStack(spacing: Brand.Spacing.micro) {
                        Text("Now")
                            .itemLabelStyle()
                        Text(String(format: "%.1fm", tide.currentHeight))
                            .highlightCaptionStyle()
                    }
                    .accessibilityLabel(String(format: "Current tide height %.1f metres", tide.currentHeight))
                } else {
                    // Shimmer skeleton
                    HStack(spacing: 16) {
                        TideEventSkeleton()
                        SkeletonBlock(width: 1, height: 44)
                        TideEventSkeleton()
                    }
                    SkeletonBlock(width: 120, height: 30)
                    .accessibilityLabel("Loading tide data")
                }
            }
            .padding(Brand.Spacing.page)
        }
        .focusable()
        .digitalCrownRotation($crownOffset)
        .brandPage()
    }

    private func tideTimesLabel(_ tide: TideData) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        let high = "High tide at \(formatter.string(from: tide.nextHighTime)), \(String(format: "%.1f", tide.nextHighHeight)) metres"
        let low = "Low tide at \(formatter.string(from: tide.nextLowTime)), \(String(format: "%.1f", tide.nextLowHeight)) metres"
        return "\(high). \(low)"
    }
}

struct TideEventView: View {
    let label: String
    let time: Date
    let height: Double

    var body: some View {
        VStack(spacing: Brand.Spacing.micro) {
            Text(label).itemLabelStyle()
            Text(time, style: .time).timeDisplayStyle()
            Text(String(format: "%.1fm", height)).highlightCaptionStyle()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label) tide: \(String(format: "%.1f", height)) metres")
    }
}

struct TideEventSkeleton: View {
    var body: some View {
        VStack(spacing: Brand.Spacing.micro) {
            SkeletonBlock(width: 30, height: 8)
            SkeletonBlock(width: 50, height: 16)
            SkeletonBlock(width: 35, height: 9)
        }
    }
}

struct TideDirectionView: View {
    let isRising: Bool
    let phase: TidePhase

    var body: some View {
        HStack(spacing: Brand.Spacing.item) {
            Image(systemName: isRising ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                .foregroundStyle(isRising ? Brand.Colors.maybe : Brand.Colors.primary)
                .font(.system(size: 18))

            VStack(alignment: .leading, spacing: 0) {
                Text(isRising ? "Incoming" : "Outgoing")
                    .font(Brand.Typography.personalityCopy)
                    .foregroundStyle(Brand.Colors.textPrimary)
                Text(phase.rawValue)
                    .captionStyle()
            }
        }
    }
}

// MARK: - Previews

#Preview {
    TidesPage()
        .previewAsWatch()
        .environment(AppState.preview())
}

#Preview("Loading") {
    TidesPage()
        .previewAsWatch()
        .environment(AppState.previewLoading())
}
