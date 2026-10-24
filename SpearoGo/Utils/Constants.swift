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

        // Cloud Functions for Spearo Go, deployed under the `spearogo` codebase
        // of the shared spearo-tracker project. See spearo-go/functions.
        static let tidesBase   = "https://us-central1-spearo-tracker.cloudfunctions.net/tidesGo"

        // Place-name search, for adding a spot you are not standing at.
        // Same provider as the weather API, no key required.
        static let geocodingBase = "https://geocoding-api.open-meteo.com/v1/search"
    }

    // MARK: - Firebase (optional sign-in and spot sync, over REST — no Firebase SDK)
    // The API key is a Firebase client identifier, not a secret: this is the
    // project's iOS key (Wear uses the Android key). Twin of Constants.Firebase in Kotlin.
    enum Firebase {
        static let apiKey          = "AIzaSyCx0FkVdpNN7jXgnJztnb622wPbw7GMBL0"
        static let projectId       = "spearo-tracker"
        // Same web client as Apnea, so one Google account gives one uid across apps.
        static let webClientId     = "420670186721-fgqn7l067ppl3mk9mm2cq1lt5ke5i0kk.apps.googleusercontent.com"
        static let identityBase    = URL(string: "https://identitytoolkit.googleapis.com")!
        static let secureTokenBase = URL(string: "https://securetoken.googleapis.com")!
        static let firestoreBase   = URL(string: "https://firestore.googleapis.com/v1/projects/\(projectId)/databases/(default)/documents")!
    }

    // MARK: - App info
    enum App {
        static let name    = "Spearo Go"
        static let version = "1.2.0"
        static let price   = "$0.99"
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
