import SwiftUI

struct OnboardingView: View {
    @Environment(AppState.self) private var appState
    @Binding var hasCompletedOnboarding: Bool

    @State private var page = 0

    var body: some View {
        TabView(selection: $page) {
            // Page 1: Welcome
            welcomePage
                .tag(0)

            // Page 2: How it works
            howItWorksPage
                .tag(1)

            // Page 3: Location permission + go
            locationPage
                .tag(2)
        }
        .tabViewStyle(.page)
        .background(Brand.Colors.background)
    }

    // MARK: - Page 1: Welcome

    private var welcomePage: some View {
        VStack(spacing: Brand.Spacing.section) {
            Spacer()

            Image(systemName: "fish.fill")
                .font(.system(size: 32))
                .foregroundStyle(Brand.Colors.primary)

            Text("Spearo Go")
                .font(Brand.Typography.verdictLabel)
                .foregroundStyle(Brand.Colors.textPrimary)

            Text("Your dive-day verdict\nin one glance.")
                .font(Brand.Typography.personalityCopy)
                .foregroundStyle(Brand.Colors.textSecondary)
                .multilineTextAlignment(.center)

            Spacer()

            Text("Swipe to continue →")
                .font(Brand.Typography.caption)
                .foregroundStyle(Brand.Colors.textSecondary)
        }
        .padding(Brand.Spacing.page)
        .brandPage()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Welcome to Spearo Go. Your dive-day verdict in one glance. Swipe to continue.")
    }

    // MARK: - Page 2: How it works

    private var howItWorksPage: some View {
        VStack(spacing: Brand.Spacing.section) {
            Text("How It Works")
                .brandSectionHeader()

            VStack(alignment: .leading, spacing: Brand.Spacing.item) {
                OnboardingBullet(icon: "wind", text: "Weather & wind")
                OnboardingBullet(icon: "water.waves", text: "Swell & marine data")
                OnboardingBullet(icon: "arrow.up.arrow.down", text: "Tide phases")
                OnboardingBullet(icon: "moon.stars", text: "Solunar fish activity")
            }

            Text("Combined into one score\nfrom 0 to 10.")
                .font(Brand.Typography.personalityCopy)
                .foregroundStyle(Brand.Colors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.top, Brand.Spacing.micro)

            HStack(spacing: Brand.Spacing.item) {
                VerdictBadge(text: "GO", color: Brand.Colors.go)
                VerdictBadge(text: "MAYBE", color: Brand.Colors.maybe)
                VerdictBadge(text: "NO GO", color: Brand.Colors.noGo)
            }
        }
        .padding(Brand.Spacing.page)
        .brandPage()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("How it works. Weather, swell, tides, and solunar data combined into one score from 0 to 10.")
    }

    // MARK: - Page 3: Location + Start

    private var locationPage: some View {
        VStack(spacing: Brand.Spacing.section) {
            Spacer()

            Image(systemName: "location.circle.fill")
                .font(.system(size: 28))
                .foregroundStyle(Brand.Colors.secondary)

            Text("Location Access")
                .font(Brand.Typography.dataValue)
                .foregroundStyle(Brand.Colors.textPrimary)

            Text("Spearo Go needs your location to fetch conditions for your dive spot.")
                .font(Brand.Typography.caption)
                .foregroundStyle(Brand.Colors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, Brand.Spacing.item)

            Spacer()

            Button {
                appState.locationService.requestLocation()
                hasCompletedOnboarding = true
            } label: {
                Text("Let's Go")
                    .font(Brand.Typography.scoreNumber)
                    .foregroundStyle(Brand.Colors.background)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Brand.Spacing.item)
                    .background(Brand.Colors.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Brand.Radius.chip))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Let's Go. Grants location access and starts the app.")
        }
        .padding(Brand.Spacing.page)
        .brandPage()
    }
}

// MARK: - Helper views

private struct OnboardingBullet: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: Brand.Spacing.item) {
            Image(systemName: icon)
                .font(.system(size: 12))
                .foregroundStyle(Brand.Colors.primary)
                .frame(width: 16)
            Text(text)
                .font(Brand.Typography.personalityCopy)
                .foregroundStyle(Brand.Colors.textPrimary)
        }
    }
}

private struct VerdictBadge: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 7, weight: .bold))
            .foregroundStyle(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(color.opacity(0.15))
            .clipShape(RoundedRectangle(cornerRadius: Brand.Radius.badge))
    }
}

// MARK: - Previews

#Preview("Onboarding") {
    OnboardingView(hasCompletedOnboarding: .constant(false))
        .previewAsWatch()
        .environment(AppState.preview())
}
