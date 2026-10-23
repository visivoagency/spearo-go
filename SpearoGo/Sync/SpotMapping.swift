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

/// A different account signed in: spots stay with the account they were saved to, so every row
/// with a cloudId (the old account's, soft-deleted ones included) leaves the watch. They are safe
/// in that account and come back if it signs in again. Rows never synced (no cloudId) stay and
/// upload to the new account. Twin of LocationDao.removeLinked in Kotlin.
/// Throws if the store can't be read or saved (rolled back): the caller must not sync on.
@MainActor
func removeLinked(context: ModelContext) throws {
    let rows = try context.fetch(FetchDescriptor<SavedLocation>(predicate: #Predicate { $0.cloudId != nil }))
    guard !rows.isEmpty else { return }
    for row in rows { context.delete(row) }
    try saveOrRollback(context)
}

/// Spots saved before v2 come out of the store with `lastModifiedAt` at its epoch default.
/// Give them their creation time, as the Wear migration does (lastModifiedAt = createdAt).
/// Throws if the store can't be read or saved: never left at 1970 silently.
@MainActor
func backfillLastModified(context: ModelContext) throws {
    let epoch = Date(timeIntervalSince1970: 0)
    let rows = try context.fetch(FetchDescriptor<SavedLocation>(
        predicate: #Predicate { $0.lastModifiedAt == epoch }))
    guard !rows.isEmpty else { return }
    for row in rows { row.lastModifiedAt = row.createdAt }
    try saveOrRollback(context)
}

/// The user removed a spot. Twin of SpotSyncService.deleteSpot in Kotlin.
/// Signed out and never linked: it is simply gone. Otherwise it is soft-deleted and waits for its
/// account (pushed when that account syncs, removed with the rest of that account's spots if
/// another one signs in).
/// Signed in, a Vision-owned spot is read-only, so nothing happens.
@MainActor
func deleteSpot(_ row: SavedLocation, signedIn: Bool, context: ModelContext) {
    if signedIn && row.origin == SpotOrigin.vision.rawValue { return }
    if signedIn || row.cloudId != nil {
        row.isDeleted = true
        row.pendingSync = true
        row.lastModifiedAt = .now
        row.isActive = false
    } else {
        context.delete(row)
    }
}

/// `rollback()` discards EVERY unsaved change in the context, not just ours. The sync layer uses
/// the app's mainContext, which is safe only because UI edits save immediately.
@MainActor
private func saveOrRollback(_ context: ModelContext) throws {
    do {
        try context.save()
    } catch {
        context.rollback()
        throw error
    }
}
