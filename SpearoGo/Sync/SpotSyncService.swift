import Foundation
import Observation
import SwiftData

// ─────────────────────────────────────────────────────────────────────────────
// SpotSyncService.swift — two-way spot sync with the Spearo account.
// Twin of wear/.../sync/SpotSyncService.kt (whose SpotSyncServiceTest pins the rules).
// Uses SwiftData, so it is NOT part of tools/spot-sync-harness.
//
// sync(), one at a time (calls queue behind each other), never throws:
//  0. backfillLastModified, before any local row is read.
//  1. Token; none and no session -> .signedOut, no requests.
//  2. Fetch Vision's top-level `spots` FIRST, then `users/{uid}/spots` (first docId
//     wins in the merge, so a Vision-owned doc stays read-only). Either fetch fails ->
//     .failed and NOTHING is applied: a partial remote list would make rule 2 delete spots.
//  3. planSync, minus rows linked to a fetched doc that doesn't decode (left untouched).
//     A pushUpsert aimed at an existing camelCase ACCOUNT doc is not pushed
//     (see withoutCamelCasePushes); the row follows the remote instead.
//  4. Push upserts, then soft deletes.
//  5. Apply locally: upsertLocal (keeping each row's isActive), deleteLocal, then the
//     successful pushes (pendingSync=false + cloudId persisted; pushed deletes removed).
//     A row the user changed while the sync was on the wire is left for the next sync,
//     except that a pushed row still without a cloudId is linked to the doc it created
//     (else the doc would be orphaned and come back as a new spot). Apply runs in one
//     synchronous main-actor block, so no UI edit can land between its read and its writes.
//     A local store error (fetch or save) is .failed("Sync failed. Try again."), never an
//     empty snapshot.
//  6. Failed pushes stay pending; state .failed("Couldn't upload N spots").
// The account marker is written on sign-in and at most once per 24 h after that.
//
// Links are scoped to an account (UserDefaults "sync.linkedUid"). Sign-out (planned, or a
// session a refresh 400 cleared) keeps every row and cloudId, so the same account signing back
// in finds its spots linked and nothing is duplicated. When a DIFFERENT account syncs,
// detachAll runs first: every live spot is kept and unlinked, so it is copied into the new
// account; the old account's pending deletes are purged. Nothing is ever deleted from the
// watch by an account change.
//
// Local reads and writes go through the app's ModelContainer's mainContext on the main
// actor: the same context the UI's @Query reads, so it never sees a stale row and the
// list updates the moment a sync lands. Network work stays off the main actor.
// Logs carry counts and status codes only: no uid, email, names, coordinates or tokens.
// ─────────────────────────────────────────────────────────────────────────────

enum SyncState: Equatable {
    case signedOut
    case idle
    case syncing
    case synced(at: Date)
    case failed(String)
}

/// What the UI observes. Written only by SpotSyncService.
@MainActor @Observable
final class SyncStatus {
    var state: SyncState
    init(state: SyncState) { self.state = state }
}

actor SpotSyncService {
    private static let markerKey = "sync.markerWrittenAt"
    /// The uid whose `users/{uid}/spots` docs the local cloudIds point at. Survives sign-out.
    private static let linkedUidKey = "sync.linkedUid"
    private static let markerInterval: TimeInterval = 24 * 60 * 60
    private static let unreachable = "Couldn't reach your account"
    private static let storeFailed = "Sync failed. Try again."

    private let auth: AuthClient
    private let firestore: FirestoreClient
    private let container: ModelContainer
    private let defaults: UserDefaults
    private let clock: @Sendable () -> Date
    private let newId: @Sendable () -> String
    nonisolated let status: SyncStatus

    /// The tail of the queue: each sync or sign-out waits for the one before it.
    private var tail: Task<Void, Never>?

    init(auth: AuthClient, firestore: FirestoreClient, container: ModelContainer, status: SyncStatus,
         defaults: UserDefaults = .standard,
         clock: @escaping @Sendable () -> Date = { Date() },
         newId: @escaping @Sendable () -> String = { UUID().uuidString }) {
        self.auth = auth
        self.firestore = firestore
        self.container = container
        self.status = status
        self.defaults = defaults
        self.clock = clock
        self.newId = newId
    }

    /// No-op when signed out; one at a time; never throws.
    func sync() async {
        await enqueue { await $0.runSync() }
    }

    /// Right after a successful sign-in: detach if the spots are linked to another account, the
    /// marker (always), then a first sync. The state is .syncing from the start, so the sign-in
    /// row can't be tapped again meanwhile. If the detach fails the sign-in is undone (.failed):
    /// syncing on with the old account's links would delete or misfile spots.
    func onSignedIn(session: Session) async {
        // In the queue, so it can't race a sign-out; skipped if one already happened.
        let proceed = await enqueue { service -> Bool in
            guard service.auth.session()?.uid == session.uid else {
                if service.auth.session() == nil { await service.setState(.signedOut) }
                return false
            }
            await service.setState(.syncing)
            guard await service.link(to: session.uid) else {
                service.auth.signOut()
                await service.setState(.failed(Self.storeFailed))
                return false
            }
            await service.writeMarker(uid: session.uid, token: session.idToken)
            return true
        }
        if proceed { await sync() }
    }

    /// Forget the session. Every spot keeps its row and its link (see the header); only the
    /// auth store and the marker time are cleared.
    func signOut() async {
        auth.signOut()
        await enqueue { await $0.signedOut() }
    }

    private func signedOut() async {
        defaults.removeObject(forKey: Self.markerKey)
        await setState(.signedOut)
    }

    /// Makes the local links belong to `uid`: if they belong to another account, detachAll
    /// first. Runs in the queue. False if the detach failed: the caller must not sync.
    private func link(to uid: String) async -> Bool {
        let linked = defaults.string(forKey: Self.linkedUidKey)
        if linked == uid { return true }
        if linked != nil {
            let container = self.container
            do {
                try await MainActor.run { try detachAll(context: container.mainContext) }
            } catch {
                print("[SpotSyncService] detach failed; not syncing")
                return false
            }
        }
        defaults.set(uid, forKey: Self.linkedUidKey)
        return true
    }

    // MARK: - Sync

    @discardableResult
    private func enqueue<T: Sendable>(_ work: @escaping @Sendable (SpotSyncService) async -> T) async -> T {
        let previous = tail
        let task = Task { [self] in
            await previous?.value
            return await work(self)
        }
        tail = Task { _ = await task.value }
        return await task.value
    }

    private func setState(_ state: SyncState) async {
        let status = self.status
        await MainActor.run { status.state = state }
    }

    private func runSync() async {
        let container = self.container
        // 0. Rows saved before v2 get their lastModifiedAt before anything reads them.
        do {
            try await MainActor.run { try backfillLastModified(context: container.mainContext) }
        } catch {
            print("[SpotSyncService] backfill failed; applied nothing")
            await setState(.failed(Self.storeFailed))
            return
        }

        // 1.
        guard let token = await auth.currentIdToken(), let uid = auth.session()?.uid else {
            await setState(auth.session() == nil ? .signedOut : .failed(Self.unreachable))
            return
        }
        await setState(.syncing)
        // The session's account may differ from the links' (e.g. the app died between sign-in
        // and onSignedIn): detach first, or fail without touching anything.
        guard await link(to: uid) else {
            await setState(.failed(Self.storeFailed))
            return
        }

        // 2. Both collections, complete, or nothing. Vision first.
        let remote: [RemoteSpot]
        var fetchedIds = Set<String>()
        do {
            let visionDocs = try await firestore.queryVisionSpots(uid: uid, token: token)
            let accountDocs = try await firestore.listUserSpots(uid: uid, token: token)
            for d in visionDocs + accountDocs { if let id = SpotCodec.docId(d) { fetchedIds.insert(id) } }
            remote = visionDocs.compactMap { SpotCodec.decode($0, origin: .vision) }
                + accountDocs.compactMap { SpotCodec.decode($0, origin: .account) }
        } catch {
            print("[SpotSyncService] fetch failed (\(error)); applied nothing")
            await setState(.failed(Self.unreachable))
            return
        }

        // 3.
        let snapshot: [LocalSpot]
        do {
            snapshot = try await MainActor.run {
                try container.mainContext.fetch(FetchDescriptor<SavedLocation>()).map { $0.toLocalSpot() }
            }
        } catch {
            print("[SpotSyncService] local read failed; applied nothing")
            await setState(.failed(Self.storeFailed))
            return
        }
        // A row linked to a doc that exists but doesn't decode (no name/latitude/longitude) is
        // left out of the merge entirely: it isn't gone (rule 2 would delete it), and we must not
        // push over a doc we can't read.
        let undecodable = fetchedIds.subtracting(remote.map(\.docId))
        if !undecodable.isEmpty { print("[SpotSyncService] \(undecodable.count) spot docs unreadable; their spots left as they are") }
        let mergeable = snapshot.filter { $0.cloudId.map { !undecodable.contains($0) } ?? true }
        let plan = Self.withoutCamelCasePushes(planSync(local: mergeable, remote: remote, newId: newId), remote: remote)
        let nowIso = SpotCodec.iso(clock())

        // 4. Pushes: upserts, then soft deletes.
        var failed = 0
        var pushedUpserts: [LocalSpot] = []
        for spot in plan.pushUpsert {
            var linked = spot
            linked.cloudId = spot.cloudId ?? spot.id
            let fields = SpotCodec.encodeUpsert(linked, uid: uid, nowIso: nowIso)
            do {
                // Masked to Go's own 12 snake_case keys, so a website doc keeps its other fields
                // (notes, photos...). It is never a camelCase doc: withoutCamelCasePushes dropped
                // those, since adding snake keys beside camel ones would leave the camel
                // isDeleted/lastModifiedAt that Tracker's restore reads stale.
                try await firestore.patch(path: "users/\(uid)/spots/\(linked.cloudId!)", fields: fields,
                                          mask: Array(fields.keys), token: token)
                linked.pendingSync = false
                pushedUpserts.append(linked)
            } catch {
                failed += 1
                print("[SpotSyncService] upload failed (\(error))")
            }
        }
        var pushedDeleteDocIds = Set<String>()
        for r in plan.pushDelete {
            let (fields, mask) = SpotCodec.encodeSoftDelete(r, nowIso: nowIso)
            do {
                try await firestore.patch(path: "users/\(uid)/spots/\(r.docId)", fields: fields, mask: mask, token: token)
                pushedDeleteDocIds.insert(r.docId)
            } catch {
                failed += 1
                print("[SpotSyncService] delete upload failed (\(error))")
            }
        }

        // 5. Apply. Signed out (or switched account) meanwhile: apply nothing.
        guard auth.session()?.uid == uid else {
            await setState(auth.session() == nil ? .signedOut : .idle)
            return
        }
        var upserts = plan.upsertLocal
        var deletes = plan.deleteLocal
        for s in pushedUpserts { if s.isDeleted { deletes.append(s.id) } else { upserts.append(s) } }
        deletes += snapshot.filter { $0.isDeleted && $0.cloudId.map(pushedDeleteDocIds.contains) == true }.map(\.id)

        let toUpsert = upserts, toDelete = deletes
        var pushedIds: [String: String] = [:]
        for s in pushedUpserts { pushedIds[s.id] = s.cloudId }
        let links = pushedIds
        let skipped: Int
        do {
            skipped = try await MainActor.run { () throws -> Int in
                try Self.apply(upserts: toUpsert, deletes: toDelete, links: links, snapshot: snapshot,
                               context: container.mainContext)
            }
        } catch {
            print("[SpotSyncService] local write failed; rolled back")
            await setState(.failed(Self.storeFailed))
            return
        }
        if skipped > 0 { print("[SpotSyncService] \(skipped) spots changed during sync; left for next time") }

        // Marker: best effort, at most once per 24 h.
        let last = defaults.object(forKey: Self.markerKey) as? Double
        if last.map({ clock().timeIntervalSince1970 - $0 >= Self.markerInterval }) ?? true {
            await writeMarker(uid: uid, token: token)
        }

        // 6.
        await setState(failed > 0
            ? .failed("Couldn't upload \(failed) \(failed == 1 ? "spot" : "spots")")
            : .synced(at: clock()))
    }

    /// Writes the plan into the store. Returns how many rows it left alone because the
    /// user changed, added or removed them since `snapshot` was taken.
    /// `links`: local id -> cloudId of each successful pushUpsert.
    @MainActor
    private static func apply(upserts: [LocalSpot], deletes: [String], links: [String: String],
                              snapshot: [LocalSpot], context: ModelContext) throws -> Int {
        let rows = try context.fetch(FetchDescriptor<SavedLocation>())
        var byId: [String: SavedLocation] = [:]
        for row in rows { byId[row.id.uuidString] = row }
        var before: [String: LocalSpot] = [:]
        for s in snapshot { before[s.id] = s }
        func untouched(_ id: String) -> Bool {
            guard let was = before[id] else { return byId[id] == nil }
            return byId[id]?.toLocalSpot() == was
        }

        var skipped = 0
        for spot in upserts {
            guard untouched(spot.id) else { skipped += 1; continue }
            if let row = byId[spot.id] {
                // Update in place: isActive is local-only and stays as it is.
                row.name = spot.name
                row.latitude = spot.latitude
                row.longitude = spot.longitude
                row.createdAt = spot.createdAt
                row.cloudId = spot.cloudId
                row.lastModifiedAt = spot.lastModifiedAt
                row.isDeleted = spot.isDeleted
                row.pendingSync = spot.pendingSync
                row.origin = spot.origin.rawValue
            } else {
                context.insert(spot.toSavedLocation(isActive: false))
            }
        }
        var seen = Set<String>()
        for id in deletes where seen.insert(id).inserted {
            guard untouched(id) else { skipped += 1; continue }
            if let row = byId[id] { context.delete(row) }
        }
        // Pushed, then edited meanwhile: still record which doc it created (only if unlinked).
        for (id, cloudId) in links where !untouched(id) {
            if let row = byId[id], row.cloudId == nil { row.cloudId = cloudId }
        }
        do {
            try context.save()
        } catch {
            // rollback() discards EVERY unsaved change in mainContext, not just this apply's.
            // Safe only because UI edits save immediately (LocationsView calls save() on each).
            context.rollback()
            throw error
        }
        return skipped
    }

    /// Ruling: a pushUpsert never targets an existing camelCase ACCOUNT doc. Such a spot follows
    /// the remote instead, as merge rule 1's else-branch would: refreshed if the doc is live,
    /// dropped locally if the doc is deleted.
    private static func withoutCamelCasePushes(_ plan: SyncPlan, remote: [RemoteSpot]) -> SyncPlan {
        var byId: [String: RemoteSpot] = [:]
        for r in remote where byId[r.docId] == nil { byId[r.docId] = r }   // first wins, as in planSync
        var out = plan
        out.pushUpsert = []
        for l in plan.pushUpsert {
            guard let r = l.cloudId.flatMap({ byId[$0] }), r.origin == .account, r.camelCase else {
                out.pushUpsert.append(l); continue
            }
            if r.isDeleted {
                out.deleteLocal.append(l.id)
            } else {
                var u = l
                u.name = r.name
                u.latitude = r.latitude
                u.longitude = r.longitude
                u.lastModifiedAt = r.lastModifiedAt ?? l.lastModifiedAt
                u.isDeleted = false
                u.pendingSync = false
                out.upsertLocal.append(u)
            }
        }
        return out
    }

    private func writeMarker(uid: String, token: String) async {
        let at = clock()
        do {
            try await firestore.patch(path: "users/\(uid)", fields: FirestoreClient.markerFields(nowIso: SpotCodec.iso(at)),
                                      mask: [FirestoreClient.markerPath], token: token)
            defaults.set(at.timeIntervalSince1970, forKey: Self.markerKey)
        } catch {
            print("[SpotSyncService] marker failed (\(error))")
        }
    }
}

// MARK: - App wiring

/// The app's one AuthClient, FirestoreClient and SpotSyncService, sharing SpearoGoApp's
/// ModelContainer with the UI. Task 7's sign-in row and triggers use these.
@MainActor
enum SyncServices {
    static let auth = AuthClient(
        baseIdentity: Constants.Firebase.identityBase,
        baseSecureToken: Constants.Firebase.secureTokenBase,
        store: KeychainTokenStore(),
        apiKey: Constants.Firebase.apiKey,
        appHeaders: AuthClient.appHeaders()
    )
    static let firestore = FirestoreClient(baseURL: Constants.Firebase.firestoreBase, appHeaders: AuthClient.appHeaders())
    static let status = SyncStatus(state: auth.session() == nil ? .signedOut : .idle)
    static let sync = SpotSyncService(auth: auth, firestore: firestore,
                                      container: SpearoGoApp.modelContainer, status: status)
    static func appleSignIn() -> AppleSignIn { AppleSignIn(auth: auth) }
}
