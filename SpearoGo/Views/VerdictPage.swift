import SwiftUI

struct VerdictPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        ZStack {
            Color(hex: Constants.Colors.background).ignoresSafeArea()

            if appState.isLoading {
                VStack(spacing: 8) {
                    ProgressView()
                        .tint(Color(hex: Constants.Colors.primaryAccent))
                    Text(PersonalityCopy.loading())
                        .font(.caption2)
                        .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                        .multilineTextAlignment(.center)
                }
            } else if let score = appState.diveScore {
                VStack(spacing: 6) {
                    Text(score.verdict.rawValue)
                        .font(.system(size: 18, weight: .black))
                        .foregroundStyle(score.verdict.color)

                    Text(PersonalityCopy.message(for: score.verdict))
                        .font(.caption)
                        .foregroundStyle(Color(hex: Constants.Colors.textPrimary))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)

                    Spacer().frame(height: 4)

                    ScoreRingView(score: score.composite)
                }
                .padding()
            } else {
                Text("Tap to load conditions")
                    .font(.caption)
                    .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
            }
        }
        .onTapGesture {
            Task { await appState.refresh() }
        }
    }
}

struct ScoreRingView: View {
    let score: Double

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color(hex: Constants.Colors.textSecondary).opacity(0.3), lineWidth: 5)
                .frame(width: 50, height: 50)
            Circle()
                .trim(from: 0, to: score / 10)
                .stroke(scoreColor, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: 50, height: 50)
            Text(String(format: "%.1f", score))
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color(hex: Constants.Colors.textPrimary))
        }
    }

    private var scoreColor: Color {
        switch score {
        case 8...: return Color(hex: Constants.Colors.Verdict.go)
        case 6...: return Color(hex: Constants.Colors.Verdict.maybe)
        case 4...: return Color(hex: Constants.Colors.Verdict.sketchy)
        default:   return Color(hex: Constants.Colors.Verdict.noGo)
        }
    }
}
