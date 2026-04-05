import SwiftUI

// ─────────────────────────────────────────────────────────────────────────────
// Typography.swift — Font extensions and text style ViewModifiers.
//
// Usage:
//   Text("GO").verdictStyle(color: Brand.Colors.go)
//   Text("WIND").itemLabelStyle()
//   Text("14").dataValueStyle()
// ─────────────────────────────────────────────────────────────────────────────

// MARK: - Text style modifiers

extension Text {
    /// Bold verdict label — "GO", "MAYBE", "SKETCHY", "NO GO"
    func verdictStyle(color: Color) -> some View {
        self
            .font(Brand.Typography.verdictLabel)
            .foregroundStyle(color)
    }

    /// Large numeric data value — "14", "1.2"
    func dataValueStyle() -> some View {
        self
            .font(Brand.Typography.dataValue)
            .foregroundStyle(Brand.Colors.textPrimary)
    }

    /// Section header — "CONDITIONS", "TIDES"
    func sectionHeaderStyle() -> some View {
        self
            .font(Brand.Typography.sectionHeader)
            .foregroundStyle(Brand.Colors.textSecondary)
            .kerning(Brand.Kerning.sectionHeader)
            .textCase(.uppercase)
    }

    /// Item label — "WIND", "HIGH", "LOW"
    func itemLabelStyle() -> some View {
        self
            .font(Brand.Typography.itemLabel)
            .foregroundStyle(Brand.Colors.textSecondary)
            .kerning(Brand.Kerning.itemLabel)
            .textCase(.uppercase)
    }

    /// Unit label — "kn", "m", "°C"
    func unitStyle() -> some View {
        self
            .font(Brand.Typography.unit)
            .foregroundStyle(Brand.Colors.textSecondary)
    }

    /// Personality copy under verdict
    func personalityStyle() -> some View {
        self
            .font(Brand.Typography.personalityCopy)
            .foregroundStyle(Brand.Colors.textPrimary)
            .multilineTextAlignment(.center)
    }

    /// Tide / time display
    func timeDisplayStyle() -> some View {
        self
            .font(Brand.Typography.timeDisplay)
            .foregroundStyle(Brand.Colors.textPrimary)
    }

    /// Small caption / hint text
    func captionStyle() -> some View {
        self
            .font(Brand.Typography.caption)
            .foregroundStyle(Brand.Colors.textSecondary)
    }

    /// Teal-coloured caption for heights / highlights
    func highlightCaptionStyle() -> some View {
        self
            .font(Brand.Typography.caption)
            .foregroundStyle(Brand.Colors.secondary)
    }
}
