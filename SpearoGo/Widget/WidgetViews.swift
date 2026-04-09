import SwiftUI
import WidgetKit

// ─────────────────────────────────────────────────────────────────────────────
// WidgetViews.swift — Smart Stack complication view variants.
// ─────────────────────────────────────────────────────────────────────────────

// MARK: - Rectangular (Smart Stack main slot)

struct RectangularWidgetView: View {
    let entry: SpearoEntry

    var body: some View {
        if let score = entry.score {
            HStack(spacing: 6) {
                // Score gauge
                Gauge(value: score.composite, in: 0...10) {
                    Text("")
                } currentValueLabel: {
                    Text(String(format: "%.1f", score.composite))
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                }
                .gaugeStyle(.accessoryCircular)
                .tint(verdictGradient(for: score.verdictEnum))

                // Verdict + timing
                VStack(alignment: .leading, spacing: 2) {
                    Text(score.verdict)
                        .font(.system(size: 14, weight: .black))
                        .foregroundStyle(colorForVerdict(score.verdictEnum))
                        .widgetAccentable()

                    Text("Spearo Go")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundStyle(.secondary)

                    Text(relativeTime(from: score.updatedAt))
                        .font(.system(size: 8))
                        .foregroundStyle(.tertiary)
                }

                Spacer(minLength: 0)
            }
        } else {
            // No data yet
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: "fish.fill")
                        .font(.system(size: 11))
                    Text("Spearo Go")
                        .font(.system(size: 12, weight: .bold))
                }
                Text("Open app to load\ndive conditions")
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - Circular (corner complication / small slot)

struct CircularWidgetView: View {
    let entry: SpearoEntry

    var body: some View {
        if let score = entry.score {
            Gauge(value: score.composite, in: 0...10) {
                Image(systemName: "fish.fill")
                    .font(.system(size: 8))
            } currentValueLabel: {
                Text(String(format: "%.0f", score.composite))
                    .font(.system(size: 14, weight: .bold, design: .rounded))
            }
            .gaugeStyle(.accessoryCircular)
            .tint(verdictGradient(for: score.verdictEnum))
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "fish.fill")
                    .font(.system(size: 14))
            }
        }
    }
}

// MARK: - Corner (watchOS corner complication)

#if os(watchOS)
struct CornerWidgetView: View {
    let entry: SpearoEntry

    var body: some View {
        if let score = entry.score {
            Text(String(format: "%.0f", score.composite))
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(colorForVerdict(score.verdictEnum))
                .widgetLabel {
                    Gauge(value: score.composite, in: 0...10) {
                        Text("Dive")
                    }
                    .tint(verdictGradient(for: score.verdictEnum))
                }
        } else {
            Image(systemName: "fish.fill")
                .font(.system(size: 16))
                .widgetLabel("Spearo Go")
        }
    }
}
#else
struct CornerWidgetView: View {
    let entry: SpearoEntry
    var body: some View { EmptyView() }
}
#endif

// MARK: - Helpers

/// Returns a verdict-appropriate color for widget rendering.
/// Uses system colors since named asset colors may not be available in the widget target.
private func colorForVerdict(_ verdict: Verdict) -> Color {
    switch verdict {
    case .go:      return .green
    case .maybe:   return .orange
    case .sketchy: return Color(red: 0.9, green: 0.5, blue: 0.13) // Brand.Colors.sketchy equivalent
    case .noGo:    return .red
    }
}

/// Gradient for gauge tinting based on verdict.
private func verdictGradient(for verdict: Verdict) -> Gradient {
    let color = colorForVerdict(verdict)
    return Gradient(colors: [color.opacity(0.7), color])
}

/// Relative time string for the widget (compact).
private func relativeTime(from date: Date) -> String {
    let elapsed = Date().timeIntervalSince(date)
    if elapsed < 60 { return "Just now" }
    let minutes = Int(elapsed / 60)
    if minutes < 60 { return "\(minutes)m ago" }
    let hours = minutes / 60
    return "\(hours)h ago"
}

// MARK: - Previews

#Preview("Rectangular — GO", as: .accessoryRectangular) {
    SpearoGoWidget()
} timeline: {
    SpearoEntry(date: Date(), score: SharedScore(composite: 8.5, verdict: "GO", updatedAt: Date()))
}

#Preview("Rectangular — NO GO", as: .accessoryRectangular) {
    SpearoGoWidget()
} timeline: {
    SpearoEntry(date: Date(), score: SharedScore(composite: 2.1, verdict: "NO GO", updatedAt: Date().addingTimeInterval(-900)))
}

#Preview("Circular — MAYBE", as: .accessoryCircular) {
    SpearoGoWidget()
} timeline: {
    SpearoEntry(date: Date(), score: SharedScore(composite: 7.0, verdict: "MAYBE", updatedAt: Date()))
}

#if os(watchOS)
#Preview("Corner — GO", as: .accessoryCorner) {
    SpearoGoWidget()
} timeline: {
    SpearoEntry(date: Date(), score: SharedScore(composite: 9.2, verdict: "GO", updatedAt: Date()))
}
#endif

#Preview("Rectangular — No Data", as: .accessoryRectangular) {
    SpearoGoWidget()
} timeline: {
    SpearoEntry(date: Date(), score: nil)
}
