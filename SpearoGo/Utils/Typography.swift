import SwiftUI

// ─────────────────────────────────────────────────────────────────────────────
// Typography.swift — Scaled font resolution and text style ViewModifiers.
//
// Usage:
//   Text("GO").verdictStyle(color: Brand.Colors.go)
//   Text("WIND").itemLabelStyle()
//   Text("14").dataValueStyle()
//   SomeView().brandFont(Brand.Typography.caption)
// ─────────────────────────────────────────────────────────────────────────────

// MARK: - Scaled font

/// Resolves a `Brand.Typography.Token` into a font that tracks the wearer's
/// text-size setting.
///
/// This exists because `Font.system(size:weight:)` — what this app used
/// everywhere until 2026-08-31 — is a fixed point size. It does not respond to
/// Accessibility → Larger Text at all, so a diver who had turned text up got
/// exactly the same 8pt labels as everyone else, with no way to make them
/// bigger. @ScaledMetric is the watchOS-supported way to scale an explicit
/// size (UIFontMetrics is unavailable here).
private struct BrandFont: ViewModifier {
    @ScaledMetric private var size: CGFloat
    private let weight: Font.Weight

    init(_ token: Brand.Typography.Token) {
        _size = ScaledMetric(wrappedValue: token.size, relativeTo: token.relativeTo)
        self.weight = token.weight
    }

    func body(content: Content) -> some View {
        content.font(.system(size: size, weight: weight))
    }
}

extension View {
    /// Apply a brand type token, scaled to the wearer's text-size setting.
    ///
    /// Note this returns `some View`, not `Text`. Any `Text`-only modifier
    /// (`.kerning`, `.tracking`) must be applied *before* this one.
    func brandFont(_ token: Brand.Typography.Token) -> some View {
        modifier(BrandFont(token))
    }
}

// MARK: - Text style modifiers

extension Text {
    /// Bold verdict label — "GO", "MAYBE", "SKETCHY", "NO GO"
    func verdictStyle(color: Color) -> some View {
        self
            .foregroundStyle(color)
            .brandFont(Brand.Typography.verdictLabel)
    }

    /// Large numeric data value — "14", "1.2"
    func dataValueStyle() -> some View {
        self
            .foregroundStyle(Brand.Colors.textPrimary)
            .brandFont(Brand.Typography.dataValue)
    }

    /// Section header — "CONDITIONS", "TIDES"
    func sectionHeaderStyle() -> some View {
        self
            .kerning(Brand.Kerning.sectionHeader)
            .foregroundStyle(Brand.Colors.textSecondary)
            .textCase(.uppercase)
            .brandFont(Brand.Typography.sectionHeader)
    }

    /// Item label — "WIND", "HIGH", "LOW"
    func itemLabelStyle() -> some View {
        self
            .kerning(Brand.Kerning.itemLabel)
            .foregroundStyle(Brand.Colors.textSecondary)
            .textCase(.uppercase)
            .brandFont(Brand.Typography.itemLabel)
    }

    /// Unit label — "kn", "m", "°C"
    func unitStyle() -> some View {
        self
            .foregroundStyle(Brand.Colors.textSecondary)
            .brandFont(Brand.Typography.unit)
    }

    /// Personality copy under verdict
    func personalityStyle() -> some View {
        self
            .foregroundStyle(Brand.Colors.textPrimary)
            .multilineTextAlignment(.center)
            .brandFont(Brand.Typography.personalityCopy)
    }

    /// Tide / time display
    func timeDisplayStyle() -> some View {
        self
            .foregroundStyle(Brand.Colors.textPrimary)
            .brandFont(Brand.Typography.timeDisplay)
    }

    /// Small caption / hint text
    func captionStyle() -> some View {
        self
            .foregroundStyle(Brand.Colors.textSecondary)
            .brandFont(Brand.Typography.caption)
    }

    /// Teal-coloured caption for heights / highlights
    func highlightCaptionStyle() -> some View {
        self
            .foregroundStyle(Brand.Colors.secondary)
            .brandFont(Brand.Typography.caption)
    }
}
