import Foundation
import SwiftData
import CoreLocation

@Model
final class SavedLocation {
    var id: UUID
    var name: String
    var latitude: Double
    var longitude: Double
    var createdAt: Date
    var isActive: Bool

    init(name: String, coordinate: CLLocationCoordinate2D) {
        self.id = UUID()
        self.name = name
        self.latitude = coordinate.latitude
        self.longitude = coordinate.longitude
        self.createdAt = Date()
        self.isActive = false
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}
