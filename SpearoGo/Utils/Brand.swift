import SwiftUI

// ─────────────────────────────────────────────────────────────────────────────
// Brand.swift — Single source of truth for all design tokens.
//
// Usage:
//   .foregroundStyle(Brand.Colors.primary)
//   .font(Brand.Typography.verdictLabel)
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
        // Verdict label — "GO", "MAYBE", "SKETCHY", "NO GO"
        static let verdictLabel = Font.system(size: 20, weight: .black)

        // Large data values — "14", "1.2", "22"
        static let dataValue    = Font.system(size: 18, weight: .bold)

        // Score ring center number
        static let scoreNumber  = Font.system(size: 14, weight: .bold)

        // Tide / solunar times — "14:32"
        static let timeDisplay  = Font.system(size: 16, weight: .bold)

        // Solunar period times
        static let periodTime   = Font.system(size: 12, weight: .medium)

        // Personality copy under verdict
        static let personalityCopy = Font.system(size: 11, weight: .regular)

        // Section headers — "CONDITIONS", "TIDES"
        static let sectionHeader = Font.system(size: 10, weight: .semibold)

        // Item labels — "WIND", "SWELL", "HIGH"
        static let itemLabel    = Font.system(size: 8, weight: .semibold)

        // Unit labels — "kn", "m", "°C"
        static let unit         = Font.system(size: 9, weight: .regular)

        // Captions — wetsuit tip, sub-labels
        static let caption      = Font.system(size: 9, weight: .regular)
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
