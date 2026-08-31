import Foundation
import CoreLocation

/// Tide predictions.
///
/// There are none yet. This used to return a synthetic curve described in its
/// own comments as a "simplified harmonic model" accurate to "±30–45 min". It
/// was neither. It anchored its M2 term to the **Unix epoch** rather than to
/// the Moon, discarded the latitude it was passed, and mapped every location on
/// earth onto a fixed 0–3m range. A customer in Lagos, Portugal reported tide
/// times matching no real sequence, which is how it was found.
///
/// It is deliberately NOT replaced by a better approximation. Spearo Vision
/// shipped the same defect and its fix carries the same instruction — see
/// `spearo-vision/lib/services/tide_service.dart`: *"Do not reintroduce a
/// fallback that fabricates."* Wrong tide times are worse than no tide times,
/// because they are indistinguishable from right ones and the dive-window
/// calculation depends on them.
///
/// Real predictions arrive with the `tidesGo` backend. See
/// `docs/superpowers/specs/2026-08-31-tide-accuracy-design.md`.
struct TideService {
    func calculate(coordinate: CLLocationCoordinate2D, date: Date = Date()) -> TideData? {
        nil
    }
}
