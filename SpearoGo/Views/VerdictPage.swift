import SwiftUI

struct VerdictPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        ZStack {
            if appState.isLoading {
                VStack(spacing: Brand.Spacing.item) {
                    ProgressView()
                        .tint(Brand.Colors.primary)
                    Text(PersonalityCopy.loading())
                        .captionStyle()
                        .multilineTextAlignment(.center)
                }
            } else if let score = appState.diveScore {
                VStack(spacing: Brand.Spacing.item) {
                    Text(score.verdict.rawValue)
                        .verdictStyle(color: Brand.Colors.forVerdict(score.verdict))

                    Text(PersonalityCopy.message(for: score.verdict))
                        .personalityStyle()
                        .padding(.horizontal, Brand.Spacing.item)

                    Spacer().frame(height: Brand.Spacing.micro)

                    ScoreRingView(score: score.composite, verdict: score.verdict)
                }
                .padding(Brand.Spacing.page)
            } else {
                Text("Tap to load conditions")
                    .captionStyle()
            }
        }
        .brandPage()
        .onTapGesture {
            Task { await appState.refresh() }
        }
    }
}

struct ScoreRingView: View {
    let score: Double
    let verdict: Verdict

    var body: some View {
        ZStack {
            Circle()
                .stroke(Brand.Colors.textSecondary.opacity(Brand.Opacity.ringTrack),
                        lineWidth: Brand.Ring.strokeWidth)
                .frame(width: Brand.Ring.size, height: Brand.Ring.size)

            Circle()
                .trim(from: 0, to: score / 10)
                .stroke(Brand.Colors.forVerdict(verdict),
                        style: StrokeStyle(lineWidth: Brand.Ring.strokeWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: Brand.Ring.size, height: Brand.Ring.size)
                .animation(.spring(duration: 0.7), value: score)

            Text(String(format: "%.1f", score))
                .font(Brand.Typography.scoreNumber)
                .foregroundStyle(Brand.Colors.textPrimary)
        }
    }
}

// MARK: - Previews

#Preview("GO") {
    VerdictPage()
        .previewAsWatch()
        .environment(AppState.preview(verdict: .go))
}

#Preview("MAYBE") {
    VerdictPage()
        .previewAsWatch()
        .environment(AppState.preview(verdict: .maybe))
}

#Preview("SKETCHY") {
    VerdictPage()
        .previewAsWatch()
        .environment(AppState.preview(verdict: .sketchy))
}

#Preview("NO GO") {
    VerdictPage()
        .previewAsWatch()
        .environment(AppState.preview(verdict: .noGo))
}

#Preview("Loading") {
    VerdictPage()
        .previewAsWatch()
        .environment(AppState.previewLoading())
}
