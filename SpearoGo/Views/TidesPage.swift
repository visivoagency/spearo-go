import SwiftUI

struct TidesPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        ZStack {
            Color(hex: Constants.Colors.background).ignoresSafeArea()

            VStack(spacing: 10) {
                Text("TIDES")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                    .kerning(1.5)

                if let tide = appState.tideData {
                    VStack(spacing: 6) {
                        HStack(spacing: 16) {
                            TideEventView(label: "HIGH", time: tide.nextHighTime, height: tide.nextHighHeight)
                            TideEventView(label: "LOW",  time: tide.nextLowTime,  height: tide.nextLowHeight)
                        }

                        TideDirectionView(isRising: tide.isRising, phase: tide.phase)
                    }
                } else {
                    ProgressView()
                        .tint(Color(hex: Constants.Colors.primaryAccent))
                }
            }
            .padding()
        }
    }
}

struct TideEventView: View {
    let label: String
    let time: Date
    let height: Double

    var body: some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.system(size: 8, weight: .semibold))
                .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
                .kerning(1)
            Text(time, style: .time)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color(hex: Constants.Colors.textPrimary))
            Text(String(format: "%.1fm", height))
                .font(.caption2)
                .foregroundStyle(Color(hex: Constants.Colors.secondaryAccent))
        }
    }
}

struct TideDirectionView: View {
    let isRising: Bool
    let phase: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: isRising ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                .foregroundStyle(isRising
                    ? Color(hex: Constants.Colors.Verdict.maybe)
                    : Color(hex: Constants.Colors.primaryAccent))
                .font(.system(size: 16))
            VStack(alignment: .leading, spacing: 0) {
                Text(isRising ? "Incoming" : "Outgoing")
                    .font(.caption2)
                    .foregroundStyle(Color(hex: Constants.Colors.textPrimary))
                Text(phase)
                    .font(.system(size: 8))
                    .foregroundStyle(Color(hex: Constants.Colors.textSecondary))
            }
        }
    }
}
