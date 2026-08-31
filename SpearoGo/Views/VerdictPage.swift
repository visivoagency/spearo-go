import SwiftUI

struct VerdictPage: View {
    @Environment(AppState.self) private var appState
    @State private var showLocations = false

    var body: some View {
        ZStack {
            if appState.isLoading {
                VStack(spacing: Brand.Spacing.item) {
                    ProgressView()
                        .tint(Brand.Colors.primary)
                    Text(appState.loadingMessage)
                        .captionStyle()
                        .multilineTextAlignment(.center)
                }
                .accessibilityLabel("Loading dive conditions")
            } else if let score = appState.diveScore {
                VStack(spacing: Brand.Spacing.item) {
                    Text(score.verdict.rawValue)
                        .verdictStyle(color: .white)
                        .accessibilityLabel("Verdict: \(score.verdict.rawValue)")

                    Text(appState.personalityMessage)
                        .personalityStyle()
                        .foregroundStyle(.white.opacity(0.9))
                        .padding(.horizontal, Brand.Spacing.item)

                    Spacer().frame(height: Brand.Spacing.micro)

                    ScoreRingView(score: score.composite, verdict: score.verdict, onColour: true)
                        .accessibilityLabel(String(format: "Dive score %.1f out of 10", score.composite))

                    // Names the signals the verdict could NOT see, so a
                    // renormalised score is never mistaken for a complete one.
                    if score.isPartial {
                        Text("Scored without \(score.missingSignals.joined(separator: " or "))")
                            .brandFont(Brand.Typography.caption)
                            .foregroundStyle(.white.opacity(0.75))
                            .multilineTextAlignment(.center)
                            .accessibilityLabel("Score does not include \(score.missingSignals.joined(separator: " or "))")
                    }

                    // Stale cache indicator
                    if let label = appState.lastRefreshedLabel {
                        Text(label)
                            .brandFont(Brand.Typography.caption)
                            .foregroundStyle(.white.opacity(appState.isStale ? 1 : 0.7))
                            .accessibilityLabel("Last updated: \(label)")
                    }

                    // GPS fallback indicator
                    if appState.isUsingFallbackLocation {
                        Text("📍 Default location")
                            .brandFont(Brand.Typography.caption)
                            .foregroundStyle(.white.opacity(0.9))
                            .accessibilityLabel("Using default location. Save a dive spot for accurate conditions.")
                    }
                }
                .padding(Brand.Spacing.page)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                // Full-bleed verdict colour with white type, matching Spearo
                // Vision's dive score card.
                .background(
                    LinearGradient(
                        colors: Brand.Colors.gradientForVerdict(score.verdict),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .ignoresSafeArea()
                )
                .accessibilityElement(children: .contain)
            } else if appState.error != nil {
                VStack(spacing: Brand.Spacing.item) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.title3)
                        .foregroundStyle(Brand.Colors.noGo)
                    Text("Couldn't load conditions")
                        .captionStyle()
                    Text("Tap to retry")
                        .brandFont(Brand.Typography.caption)
                        .foregroundStyle(Brand.Colors.textSecondary)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Failed to load conditions. Double tap to retry.")
            } else {
                Text("Tap to load conditions")
                    .captionStyle()
                    .accessibilityHint("Double tap to refresh dive conditions")
            }
        }
        .brandPage()
        .onTapGesture {
            Task { await appState.refresh() }
        }
        .onLongPressGesture {
            showLocations = true
        }
        .sheet(isPresented: $showLocations) {
            LocationsView()
                .environment(appState)
        }
    }
}

struct ScoreRingView: View {
    let score: Double
    let verdict: Verdict
    /// Drawn on top of the verdict colour, so the ring is white rather than
    /// the verdict hue it would otherwise disappear into.
    var onColour: Bool = false

    private var trackColour: Color {
        onColour ? .white.opacity(0.3) : Brand.Colors.textSecondary.opacity(Brand.Opacity.ringTrack)
    }
    private var progressColour: Color {
        onColour ? .white : Brand.Colors.forVerdict(verdict)
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(trackColour, lineWidth: Brand.Ring.strokeWidth)
                .frame(width: Brand.Ring.size, height: Brand.Ring.size)

            Circle()
                .trim(from: 0, to: score / 10)
                .stroke(progressColour,
                        style: StrokeStyle(lineWidth: Brand.Ring.strokeWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: Brand.Ring.size, height: Brand.Ring.size)
                .animation(.spring(duration: 0.7), value: score)

            Text(String(format: "%.1f", score))
                .brandFont(Brand.Typography.scoreNumber)
                .foregroundStyle(onColour ? .white : Brand.Colors.textPrimary)
                // Brand.Ring.size is a fixed 58pt, so the score has to shrink
                // to fit rather than overflow the ring at large text sizes.
                .lineLimit(1)
                .minimumScaleFactor(0.6)
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
