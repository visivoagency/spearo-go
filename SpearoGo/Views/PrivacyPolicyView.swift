import SwiftUI

struct PrivacyPolicyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Brand.Spacing.section) {
                Text("PRIVACY POLICY")
                    .brandSectionHeader()

                policySection(
                    title: "Location Data",
                    body: "Spearo Go uses your location solely to fetch weather, marine, and tide conditions for your current position. Your coordinates are sent to Open-Meteo APIs over HTTPS and are never stored on any server."
                )

                policySection(
                    title: "Saved Locations",
                    body: "Dive spots you save are stored locally on your Apple Watch using SwiftData. They are never uploaded or shared with any third party."
                )

                policySection(
                    title: "No Accounts",
                    body: "Spearo Go does not require or create any user accounts. There is no sign-up, no email collection, and no personal data stored off-device."
                )

                policySection(
                    title: "No Analytics",
                    body: "Spearo Go does not include any analytics, tracking, or advertising SDKs. We do not collect usage data of any kind."
                )

                policySection(
                    title: "Third-Party APIs",
                    body: "Weather and marine data are fetched from Open-Meteo (open-meteo.com), a free and open-source weather API. Tide and solunar calculations are performed entirely on-device."
                )

                policySection(
                    title: "Contact",
                    body: "Questions? Reach us at https://spearotracker.com/contact"
                )

                Text("Effective: January 2025")
                    .font(Brand.Typography.caption)
                    .foregroundStyle(Brand.Colors.textSecondary)
            }
            .padding(Brand.Spacing.page)
        }
        .background(Brand.Colors.background)
        .navigationTitle("Privacy")
    }

    private func policySection(title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: Brand.Spacing.micro) {
            Text(title)
                .font(Brand.Typography.itemLabel)
                .foregroundStyle(Brand.Colors.primary)
                .kerning(Brand.Kerning.itemLabel)

            Text(body)
                .font(Brand.Typography.caption)
                .foregroundStyle(Brand.Colors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Previews

#Preview("Privacy Policy") {
    PrivacyPolicyView()
        .previewAsWatch()
}
