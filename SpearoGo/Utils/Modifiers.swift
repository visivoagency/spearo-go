import SwiftUI

// ─────────────────────────────────────────────────────────────────────────────
// Modifiers.swift — Reusable ViewModifiers that enforce brand consistency.
//
// Usage:
//   VStack { ... }.brandPage()
//   HStack { ... }.brandCard()
//   Text("...").brandChip(color: Brand.Colors.go)
// ─────────────────────────────────────────────────────────────────────────────

// MARK: - Page background

struct BrandPageModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Brand.Colors.background.ignoresSafeArea())
    }
}

extension View {
    /// Apply the full-bleed black background to a page.
    func brandPage() -> some View {
        modifier(BrandPageModifier())
    }
}

// MARK: - Card surface

struct BrandCardModifier: ViewModifier {
    var padding: CGFloat = Brand.Spacing.item

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: Brand.Radius.card)
                    .fill(Brand.Colors.textPrimary.opacity(Brand.Opacity.cardFill))
            )
            .overlay(
                RoundedRectangle(cornerRadius: Brand.Radius.card)
                    .strokeBorder(
                        Brand.Colors.textPrimary.opacity(Brand.Opacity.borderLine),
                        lineWidth: 1
                    )
            )
    }
}

extension View {
    /// Wrap content in a subtle dark card with a hairline border.
    func brandCard(padding: CGFloat = Brand.Spacing.item) -> some View {
        modifier(BrandCardModifier(padding: padding))
    }
}

// MARK: - Verdict chip / badge

struct VerdictChipModifier: ViewModifier {
    let color: Color

    func body(content: Content) -> some View {
        content
            .font(Brand.Typography.itemLabel)
            .kerning(Brand.Kerning.itemLabel)
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(
                RoundedRectangle(cornerRadius: Brand.Radius.badge)
                    .fill(color.opacity(0.12))
            )
            .overlay(
                RoundedRectangle(cornerRadius: Brand.Radius.badge)
                    .strokeBorder(color.opacity(0.25), lineWidth: 1)
            )
    }
}

extension View {
    /// Verdict badge chip with colour-matched fill and border.
    func verdictChip(color: Color) -> some View {
        modifier(VerdictChipModifier(color: color))
    }
}

// MARK: - Teal info pill

struct InfoPillModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(Brand.Typography.caption)
            .foregroundStyle(Brand.Colors.secondary)
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(
                RoundedRectangle(cornerRadius: Brand.Radius.chip)
                    .fill(Brand.Colors.secondary.opacity(0.08))
            )
            .overlay(
                RoundedRectangle(cornerRadius: Brand.Radius.chip)
                    .strokeBorder(Brand.Colors.secondary.opacity(0.15), lineWidth: 1)
            )
    }
}

extension View {
    /// Teal info pill — used for wetsuit tips, notes, sub-hints.
    func infoPill() -> some View {
        modifier(InfoPillModifier())
    }
}

// MARK: - Section header with top spacing

struct SectionHeaderModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(Brand.Typography.sectionHeader)
            .foregroundStyle(Brand.Colors.textSecondary)
            .kerning(Brand.Kerning.sectionHeader)
            .textCase(.uppercase)
            .padding(.bottom, Brand.Spacing.item)
    }
}

extension View {
    /// Styled section header label with standard bottom padding.
    func brandSectionHeader() -> some View {
        modifier(SectionHeaderModifier())
    }
}

// MARK: - Loading overlay

struct LoadingOverlayModifier: ViewModifier {
    let isLoading: Bool
    let message: String

    func body(content: Content) -> some View {
        ZStack {
            content
            if isLoading {
                VStack(spacing: Brand.Spacing.item) {
                    ProgressView()
                        .tint(Brand.Colors.primary)
                    Text(message)
                        .font(Brand.Typography.caption)
                        .foregroundStyle(Brand.Colors.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Brand.Colors.background.opacity(0.85))
            }
        }
    }
}

extension View {
    /// Overlay a branded loading spinner + message while `isLoading` is true.
    func brandLoading(isLoading: Bool, message: String = "Checking the vibes...") -> some View {
        modifier(LoadingOverlayModifier(isLoading: isLoading, message: message))
    }
}

// MARK: - Shimmer / skeleton loading placeholder

struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlay(
                LinearGradient(
                    colors: [
                        .clear,
                        Brand.Colors.textSecondary.opacity(0.15),
                        .clear
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .offset(x: phase)
                .onAppear {
                    withAnimation(
                        .linear(duration: 1.2)
                        .repeatForever(autoreverses: false)
                    ) {
                        phase = 200
                    }
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: Brand.Radius.chip))
    }
}

extension View {
    /// Applies a shimmer animation — use on skeleton placeholder shapes.
    func shimmer() -> some View {
        modifier(ShimmerModifier())
    }
}

// MARK: - Skeleton placeholder shape

struct SkeletonBlock: View {
    var width: CGFloat = 50
    var height: CGFloat = 14

    var body: some View {
        RoundedRectangle(cornerRadius: Brand.Radius.badge)
            .fill(Brand.Colors.textSecondary.opacity(0.12))
            .frame(width: width, height: height)
            .shimmer()
    }
}

/// A skeleton version of a ConditionItem — icon + two placeholder bars.
struct ConditionItemSkeleton: View {
    var body: some View {
        VStack(spacing: Brand.Spacing.micro) {
            SkeletonBlock(width: 14, height: 14)
            SkeletonBlock(width: 30, height: 8)
            SkeletonBlock(width: 40, height: 18)
        }
        .frame(minWidth: 60)
    }
}
