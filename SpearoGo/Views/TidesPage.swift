import SwiftUI

struct TidesPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
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

                TideDirectionView(isRising: tide.isRising, phase: tide.phase)
                    .brandCard(padding: Brand.Spacing.item)
            } else {
                ProgressView().tint(Brand.Colors.primary)
            }
        }
        .padding(Brand.Spacing.page)
        .brandPage()
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
    }
}

struct TideDirectionView: View {
    let isRising: Bool
    let phase: String

    var body: some View {
        HStack(spacing: Brand.Spacing.item) {
            Image(systemName: isRising ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                .foregroundStyle(isRising ? Brand.Colors.maybe : Brand.Colors.primary)
                .font(.system(size: 18))

            VStack(alignment: .leading, spacing: 0) {
                Text(isRising ? "Incoming" : "Outgoing")
                    .font(Brand.Typography.personalityCopy)
                    .foregroundStyle(Brand.Colors.textPrimary)
                Text(phase)
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
