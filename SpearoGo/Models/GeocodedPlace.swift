import Foundation
import CoreLocation

/// A place returned by a name search.
///
/// `region` and `country` are not decoration. Searching "Lagos" returns five
/// places — Nigeria, France, Spain, Portugal and Greece — and the Portuguese
/// one an Algarve diver wants is fourth. Confirmed on a watch, where the first
/// three rows were the wrong continent.
struct GeocodedPlace: Identifiable, Equatable {
    let id: String
    let name: String
    let region: String?
    let country: String?
    let latitude: Double
    let longitude: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    /// "Faro District, PT" — enough to tell five Lagoses apart.
    var label: String {
        let parts = [region, country].compactMap { $0 }
        return parts.isEmpty ? name : parts.joined(separator: ", ")
    }

    /// What gets stored as the spot's name.
    var savedName: String {
        [name, region].compactMap { $0 }.joined(separator: ", ")
    }
}
