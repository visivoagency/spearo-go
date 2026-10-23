import Foundation
import SwiftData

// SavedLocation <-> LocalSpot. Twin of wear/.../sync/SpotMapping.kt.
// Uses SwiftData, so it is NOT part of tools/spot-sync-harness (Foundation only).

extension SavedLocation {
    /// `origin` holds the SpotOrigin raw value; one we do not know reads as GO, never as a remote origin.
    func toLocalSpot() -> LocalSpot {
        LocalSpot(
            id: id.uuidString, cloudId: cloudId, name: name,
            latitude: latitude, longitude: longitude,
            createdAt: createdAt, lastModifiedAt: lastModifiedAt,
            isDeleted: isDeleted, pendingSync: pendingSync,
            origin: SpotOrigin(rawValue: origin) ?? .go
        )
    }
}

extension LocalSpot {
    /// isActive is local-only state, so the caller says what it should be.
    /// `id` must be a UUID string (the sync layer's `newId` generates them).
    func toSavedLocation(isActive: Bool) -> SavedLocation {
        SavedLocation(
            id: UUID(uuidString: id) ?? UUID(), name: name,
            latitude: latitude, longitude: longitude,
            createdAt: createdAt, isActive: isActive,
            cloudId: cloudId, lastModifiedAt: lastModifiedAt,
            isDeleted: isDeleted, pendingSync: pendingSync, origin: origin.rawValue
        )
    }
}

/// Sign-out: forget the account. Spots stay on the watch; pending deletes are dropped.
func detachAll(context: ModelContext) {
    let rows = (try? context.fetch(FetchDescriptor<SavedLocation>())) ?? []
    for row in rows {
        if row.isDeleted {
            context.delete(row)
        } else {
            row.cloudId = nil
            row.pendingSync = false
        }
    }
    try? context.save()
}

/// Spots saved before v2 come out of the store with `lastModifiedAt` at its epoch default.
/// Give them their creation time, as the Wear migration does (lastModifiedAt = createdAt).
func backfillLastModified(context: ModelContext) {
    let epoch = Date(timeIntervalSince1970: 0)
    let rows = (try? context.fetch(FetchDescriptor<SavedLocation>(
        predicate: #Predicate { $0.lastModifiedAt == epoch }))) ?? []
    guard !rows.isEmpty else { return }
    for row in rows { row.lastModifiedAt = row.createdAt }
    try? context.save()
}
