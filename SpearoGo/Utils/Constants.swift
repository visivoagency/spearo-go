import SwiftUI

// ─────────────────────────────────────────────────────────────────────────────
// Constants.swift — Non-visual constants: API URLs, score weights, app meta.
//
// For all colors, fonts, and spacing use Brand.swift instead.
// ─────────────────────────────────────────────────────────────────────────────

enum Constants {
    // MARK: - Score weights  (see also Brand.swift for visual tokens)
    enum Weights {
        static let weather: Double = 0.30
        static let marine:  Double = 0.30
        static let tides:   Double = 0.15
        static let solunar: Double = 0.25
    }

    // MARK: - API base URLs
    enum API {
        static let weatherBase = "https://api.open-meteo.com/v1/forecast"
        static let marineBase  = "https://marine-api.open-meteo.com/v1/marine"
    }

    // MARK: - App info
    enum App {
        static let name    = "Spearo Go"
        static let version = "1.0.0"
        static let price   = "$2.99"
    }
}

// MARK: - Color(hex:) — kept for legacy use & API validation spike only.
// In views, always use Brand.Colors.* or Color("NamedColor") instead.

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r, g, b: Double
        switch hex.count {
        case 6:
            r = Double((int >> 16) & 0xFF) / 255
            g = Double((int >>  8) & 0xFF) / 255
            b = Double( int        & 0xFF) / 255
        default:
            r = 1; g = 1; b = 1
        }
        self.init(red: r, green: g, blue: b)
    }
}
