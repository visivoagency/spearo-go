import SwiftUI

struct TidesPage: View {
    // Matched to the high/low columns, which are taller now the type is bigger.
    @ScaledMetric(relativeTo: .title3) private var dividerHeight: CGFloat = 52

    @Environment(AppState.self) private var appState
    @State private var crownOffset: Double = 0

    var body: some View {
        ScrollView {
            VStack(spacing: Brand.Spacing.section) {
                Text("Tides")
                    .brandSectionHeader()

                if let tide = appState.tideData {
                    HStack(spacing: 16) {
                        TideEventView(label: "HIGH", event: tide.nextHigh(), tide: tide)

                        Divider()
                            .frame(height: dividerHeight)
                            .background(Brand.Colors.textSecondary.opacity(Brand.Opacity.borderLine))

                        TideEventView(label: "LOW", event: tide.nextLow(), tide: tide)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(tideTimesLabel(tide))

                    TideDirectionView(isRising: tide.isRising(), phase: tide.phase())
                        .brandCard(padding: Brand.Spacing.item)
                        .accessibilityLabel("Tide is \(tide.isRising() ? "incoming" : "outgoing"), phase: \(tide.phase().rawValue)")

                    // Current height
                    HStack(spacing: Brand.Spacing.micro) {
                        Text("Now")
                            .itemLabelStyle()
                        Text(tide.currentHeight().map { String(format: "%.1fm", $0) } ?? "—")
                            .highlightCaptionStyle()
                    }
                    .accessibilityLabel(tide.currentHeight().map { String(format: "Current tide height %.1f metres", $0) } ?? "Current tide height not reported")

                    if let source = sourceLabel(tide) {
                        Text(source)
                            .captionStyle()
                            .lineLimit(1)
                    }
                } else if appState.isLoading {
                    // Shimmer skeleton
                    HStack(spacing: 16) {
                        TideEventSkeleton()
                        SkeletonBlock(width: 1, height: 44)
                        TideEventSkeleton()
                    }
                    SkeletonBlock(width: 120, height: 30)
                    .accessibilityLabel("Loading tide data")
                } else {
                    // Not a loading state. The synthetic tide model this page
                    // used to draw was wrong everywhere on earth, so it was
                    // removed rather than improved. Real predictions arrive
                    // with the tidesGo backend.
                    Text("No tide data for this spot yet")
                        .captionStyle()
                        .multilineTextAlignment(.center)
                        .accessibilityLabel("No tide data for this spot yet")
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
        func describe(_ name: String, _ event: TideEvent?) -> String {
            guard let event else { return "No further \(name) tide today" }
            return "\(name) tide at \(TideEventView.stationTime.string(from: tide.stationLocalDate(event))), "
                + "\(String(format: "%.1f", event.height)) metres"
        }
        return "\(describe("High", tide.nextHigh())). \(describe("Low", tide.nextLow()))"
    }

    /// Where the numbers came from. A named gauge and an ocean-model estimate
    /// must never read as equally trustworthy: the model is materially weaker
    /// in the estuaries and inlets these users dive.
    private func sourceLabel(_ tide: TideData) -> String? {
        if tide.isStale { return "Saved forecast" }
        if tide.isModelEstimate { return "Estimated · no nearby gauge" }
        return tide.stationName
    }
}

struct TideEventView: View {
    /// UTC, because the value handed to it is already shifted into the
    /// station's local time.
    static let stationTime: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        f.timeZone = TimeZone(secondsFromGMT: 0)
        return f
    }()

    let label: String
    /// nil after the day's last turn — shown as "—" rather than wrapped round
    /// to a time that has already passed.
    let event: TideEvent?
    /// Carries the station's UTC offset, so the time shown is the one the local
    /// tide table prints rather than the reader's wall clock.
    let tide: TideData

    var body: some View {
        VStack(spacing: Brand.Spacing.micro) {
            Text(label).itemLabelStyle()
            if let event {
                Text(Self.stationTime.string(from: tide.stationLocalDate(event)))
                    .timeDisplayStyle()
                Text(String(format: "%.1fm", event.height)).highlightCaptionStyle()
            } else {
                Text("—").timeDisplayStyle()
                Text(" ").highlightCaptionStyle()
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(event.map { "\(label) tide: \(String(format: "%.1f", $0.height)) metres" }
                            ?? "No further \(label.lowercased()) tide today")
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
                    .brandFont(Brand.Typography.personalityCopy)
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
