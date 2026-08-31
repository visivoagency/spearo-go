import SwiftUI

// ─────────────────────────────────────────────────────────────────────────────
// Brand.swift — Single source of truth for all design tokens.
//
// Usage:
//   .foregroundStyle(Brand.Colors.primary)
//   .brandFont(Brand.Typography.verdictLabel)
//   .padding(Brand.Spacing.page)
//
// All colors are backed by named color sets in Assets.xcassets/Colors/.
// Changing a color set in Xcode instantly updates every screen.
// ─────────────────────────────────────────────────────────────────────────────

enum Brand {

    // ─── Colors ──────────────────────────────────────────────────────────────

    enum Colors {
        // Base
        static let background  = Color("Background")
        static let primary     = Color("OceanBlue")     // #0077B6
        static let secondary   = Color("Teal")          // #00B4D8

        // Text
        static let textPrimary   = Color("TextPrimary")
        static let textSecondary = Color("TextSecondary")

        // Verdicts
        static let go      = Color("VerdictGo")      // #2ECC71
        static let maybe   = Color("VerdictMaybe")   // #F39C12
        static let sketchy = Color("VerdictSketchy") // #E67E22
        static let noGo    = Color("VerdictNoGo")    // #E74C3C

        // Semantic aliases — use these in views rather than raw verdict names
        static let safe      = go
        static let caution   = maybe
        static let warning   = sketchy
        static let danger    = noGo
        static let accent    = secondary

        // Convenience: verdict color from DiveScore.Verdict
        static func forVerdict(_ verdict: Verdict) -> Color {
            switch verdict {
            case .go:      return go
            case .maybe:   return maybe
            case .sketchy: return sketchy
            case .noGo:    return noGo
            }
        }
    }

    // ─── Typography ──────────────────────────────────────────────────────────

    enum Typography {
        // A type token, not a Font. Fonts are resolved per-view by `brandFont`,
        // which scales `size` against the wearer's text-size setting via
        // @ScaledMetric. `Font.system(size:)` is a FIXED size and ignores
        // Accessibility -> Larger Text entirely, which is why these were
        // unreadable for anyone who had turned it up.
        //
        // `relativeTo` picks the metric the size scales along, so a 24pt
        // verdict and an 11pt label grow at rates appropriate to their role
        // rather than all multiplying uniformly.
        struct Token {
            let size: CGFloat
            let weight: Font.Weight
            let relativeTo: Font.TextStyle
        }

        // Sizes raised 2026-08-31 after a field report that the watch UI was
        // too small to read. Nothing sits below 11pt now; the old scale
        // bottomed out at 8pt, well under the ~12pt watchOS legibility floor.
        // Prior values are noted so the hierarchy stays auditable.

        // Verdict label — "GO", "MAYBE", "SKETCHY", "NO GO"      (was 20)
        static let verdictLabel = Token(size: 24, weight: .black, relativeTo: .title2)

        // Large data values — "14", "1.2", "22"                  (was 18)
        static let dataValue = Token(size: 21, weight: .bold, relativeTo: .title3)

        // Score ring centre number                               (was 14)
        static let scoreNumber = Token(size: 17, weight: .bold, relativeTo: .headline)

        // Tide / solunar times — "14:32"                         (was 16)
        static let timeDisplay = Token(size: 19, weight: .bold, relativeTo: .title3)

        // Solunar period times                                   (was 12)
        static let periodTime = Token(size: 14, weight: .medium, relativeTo: .body)

        // Personality copy under verdict                         (was 11)
        static let personalityCopy = Token(size: 13, weight: .regular, relativeTo: .body)

        // Section headers — "CONDITIONS", "TIDES"                (was 10)
        static let sectionHeader = Token(size: 12, weight: .semibold, relativeTo: .caption)

        // Item labels — "WIND", "SWELL", "HIGH"                  (was 8)
        static let itemLabel = Token(size: 11, weight: .semibold, relativeTo: .caption2)

        // Unit labels — "kn", "m", "°C"                          (was 9)
        static let unit = Token(size: 11, weight: .regular, relativeTo: .caption2)

        // Captions — wetsuit tip, sub-labels, body copy          (was 9)
        static let caption = Token(size: 12, weight: .regular, relativeTo: .caption)
    }

    // ─── Spacing ─────────────────────────────────────────────────────────────

    enum Spacing {
        static let page:    CGFloat = 12   // view edge padding
        static let section: CGFloat = 10   // between major elements
        static let item:    CGFloat = 6    // between related items
        static let micro:   CGFloat = 2    // label-value gap
    }

    // ─── Letter spacing ──────────────────────────────────────────────────────

    enum Kerning {
        static let sectionHeader: CGFloat = 2.0
        static let itemLabel:     CGFloat = 1.0
        static let unitLabel:     CGFloat = 0.5
    }

    // ─── Corner radii ────────────────────────────────────────────────────────

    enum Radius {
        static let card:  CGFloat = 12
        static let chip:  CGFloat = 8
        static let badge: CGFloat = 6
        static let pill:  CGFloat = 100
    }

    // ─── Opacity ─────────────────────────────────────────────────────────────

    enum Opacity {
        static let ringTrack:  Double = 0.30
        static let cardFill:   Double = 0.04
        static let borderLine: Double = 0.08
        static let disabled:   Double = 0.35
    }

    // ─── Score ring dimensions ────────────────────────────────────────────────

    enum Ring {
        static let size:        CGFloat = 58
        static let strokeWidth: CGFloat = 5
        static let radius:      CGFloat = 24
    }
}
