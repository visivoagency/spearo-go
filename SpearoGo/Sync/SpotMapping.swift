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
    /// isActive is local-only state, and ownerUid is the account the row belongs to (the merge
    /// does not carry it), so the caller says what each should be.
    /// `id` must be a UUID string (the sync layer's `newId` generates them).
    func toSavedLocation(isActive: Bool, ownerUid: String?) -> SavedLocation {
        SavedLocation(
            id: UUID(uuidString: id) ?? UUID(), name: name,
            latitude: latitude, longitude: longitude,
            createdAt: createdAt, isActive: isActive,
            cloudId: cloudId, lastModifiedAt: lastModifiedAt,
            isDeleted: isDeleted, pendingSync: pendingSync, origin: origin.rawValue,
            ownerUid: ownerUid
        )
    }
}

/// The account changed to `newUid`. Spots stay with the account they were saved to (owner
/// ruling, 23 Oct), so for each row owned by another uid:
///  - synced (pendingSync false): removed. It is safe in that account and comes back when it
///    signs in again;
///  - not yet uploaded (pendingSync true, a new spot or a delete): kept but hidden
///    (`isVisible(to:)`) and made inactive. It is never shown or uploaded to `newUid`, and
///    uploads to its own account when that account signs back in.
/// When `includeLinked` (the links belonged to another account), linked rows with no owner
/// (written before owners were recorded) are removed too. Rows saved while signed out (no
/// cloudId, no ownerUid) stay and upload to the new account. Rows owned by `newUid` are untouched.
/// Twin of LocationDao.removeAccountSpots in Kotlin.
/// Throws if the store can't be read or saved (rolled back): the caller must not sync on.
@MainActor
func removeAccountSpots(newUid: String, includeLinked: Bool, context: ModelContext) throws {
    var changed = false
    for row in try context.fetch(FetchDescriptor<SavedLocation>()) {
        if let owner = row.ownerUid {
            guard owner != newUid else { continue }
            if !row.pendingSync {
                context.delete(row); changed = true
            } else if row.isActive {
                row.isActive = false; changed = true
            }
        } else if includeLinked && row.cloudId != nil {
            context.delete(row); changed = true
        }
    }
    guard changed else { return }
    try saveOrRollback(context)
}

extension SavedLocation {
    /// Shown on the watch: spots of `viewerUid` (the linked account, "sync.linkedUid") and those
    /// saved while signed out. Another account's unsynced spots stay hidden until it signs back
    /// in. Nil `viewerUid` (never signed in): all. Twin of LocationDao.getVisibleLocations.
    func isVisible(to viewerUid: String?) -> Bool {
        guard let viewerUid, let ownerUid else { return true }
        return ownerUid == viewerUid
    }
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
/// account (pushed when that account syncs, kept hidden for it if another one signs in first).
/// Signed in (`sessionUid` set), a Vision-owned spot is read-only, so nothing happens. A non-nil
/// `ownerUid` (SyncServices.ownerForEdits()) is stamped on the row.
@MainActor
func deleteSpot(_ row: SavedLocation, sessionUid: String?, ownerUid: String?, context: ModelContext) {
    let signedIn = sessionUid != nil
    if signedIn && row.origin == SpotOrigin.vision.rawValue { return }
    if let ownerUid { row.ownerUid = ownerUid }
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
