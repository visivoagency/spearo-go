import SwiftUI

struct PrivacyPolicyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Brand.Spacing.section) {
                Text("PRIVACY POLICY")
                    .brandSectionHeader()

                policySection(
                    title: "Location Data",
                    body: "Spearo Go uses your location solely to fetch weather, marine, and tide conditions for your current position. Your coordinates are sent over HTTPS to Open-Meteo for weather and sea state (and, if you search for a place by name, the text you type goes to Open-Meteo's geocoding service), and to Spearo's own tide service, which passes them to NOAA or WorldTides. The tide service rounds your position to about one kilometre and keeps that rounded point for 24 hours so the same spot is not looked up repeatedly. The tide service also keeps a one-way hash of your IP address for up to about two hours to limit abuse. Neither is linked to you."
                )

                policySection(
                    title: "Saved Locations",
                    body: "Dive spots are stored on your watch. If you sign in, they are also kept in your Spearo account so you can see them at spearo.pro. They are private and never shared."
                )

                policySection(
                    title: "No Tracking",
                    body: "No ads, no analytics. An account is optional; if you sign in, your email and saved spots are linked to it."
                )

                policySection(
                    title: "No Analytics",
                    body: "Spearo Go does not include any analytics, tracking, or advertising SDKs. We do not collect usage data of any kind."
                )

                policySection(
                    title: "Third-Party APIs",
                    body: "Weather and marine data are fetched from Open-Meteo (open-meteo.com), a free and open-source weather API. Solunar is calculated on-device. Tides come from NOAA or WorldTides via Spearo's tide service."
                )

                policySection(
                    title: "Contact",
                    body: "Questions? Reach us at https://spearotracker.com/contact"
                )

                Text("Effective: January 2025")
                    .brandFont(Brand.Typography.caption)
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
                .brandFont(Brand.Typography.itemLabel)
                .foregroundStyle(Brand.Colors.primary)
                .kerning(Brand.Kerning.itemLabel)

            Text(body)
                .brandFont(Brand.Typography.caption)
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
