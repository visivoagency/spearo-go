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

    // Added in spot model v2. Every property carries a default in its declaration,
    // which is what lets SwiftData migrate existing stores without losing a spot.
    /// Firestore doc id of this spot's account twin; nil until it has one.
    var cloudId: String? = nil
    /// Epoch until set. Rows saved before v2 are backfilled to `createdAt` (see `backfillLastModified`).
    var lastModifiedAt: Date = Date(timeIntervalSince1970: 0)
    /// Soft delete: the row waits until the delete has reached the account.
    var isDeleted: Bool = false
    var pendingSync: Bool = false
    /// A `SpotOrigin` raw value: "GO", "ACCOUNT" or "VISION".
    var origin: String = "GO"

    init(name: String, coordinate: CLLocationCoordinate2D) {
        self.id = UUID()
        self.name = name
        self.latitude = coordinate.latitude
        self.longitude = coordinate.longitude
        let now = Date()
        self.createdAt = now
        self.lastModifiedAt = now
        self.isActive = false
    }

    /// Rebuilds a row from a merged `LocalSpot` (see SpotMapping.swift).
    init(id: UUID, name: String, latitude: Double, longitude: Double, createdAt: Date, isActive: Bool,
         cloudId: String?, lastModifiedAt: Date, isDeleted: Bool, pendingSync: Bool, origin: String) {
        self.id = id
        self.name = name
        self.latitude = latitude
        self.longitude = longitude
        self.createdAt = createdAt
        self.isActive = isActive
        self.cloudId = cloudId
        self.lastModifiedAt = lastModifiedAt
        self.isDeleted = isDeleted
        self.pendingSync = pendingSync
        self.origin = origin
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}
