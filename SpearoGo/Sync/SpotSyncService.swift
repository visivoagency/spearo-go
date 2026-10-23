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
//  3. planSync.
//  4. Push upserts, then soft deletes.
//  5. Apply locally: upsertLocal (keeping each row's isActive), deleteLocal, then the
//     successful pushes (pendingSync=false + cloudId persisted; pushed deletes removed).
//     A row the user changed while the sync was on the wire is left for the next sync.
//  6. Failed pushes stay pending; state .failed("Couldn't upload N spots").
// The account marker is written on sign-in and at most once per 24 h after that.
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
    private static let markerInterval: TimeInterval = 24 * 60 * 60
    private static let unreachable = "Couldn't reach your account"

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

    /// Right after a successful sign-in: the marker (always), then a first sync.
    func onSignedIn(session: Session) async {
        await writeMarker(uid: session.uid, token: session.idToken)
        await sync()
    }

    /// Forget the account. Spots stay on the watch; links and pending deletes go.
    func signOut() async {
        auth.signOut()
        await enqueue { await $0.detach() }
    }

    private func detach() async {
        let container = self.container
        await MainActor.run { detachAll(context: container.mainContext) }
        defaults.removeObject(forKey: Self.markerKey)
        await setState(.signedOut)
    }

    // MARK: - Sync

    private func enqueue(_ work: @escaping @Sendable (SpotSyncService) async -> Void) async {
        let previous = tail
        let task = Task { [self] in
            await previous?.value
            await work(self)
        }
        tail = task
        await task.value
    }

    private func setState(_ state: SyncState) async {
        let status = self.status
        await MainActor.run { status.state = state }
    }

    private func runSync() async {
        let container = self.container
        // 0. Rows saved before v2 get their lastModifiedAt before anything reads them.
        await MainActor.run { backfillLastModified(context: container.mainContext) }

        // 1.
        guard let token = await auth.currentIdToken(), let uid = auth.session()?.uid else {
            await setState(auth.session() == nil ? .signedOut : .failed(Self.unreachable))
            return
        }
        await setState(.syncing)

        // 2. Both collections, complete, or nothing. Vision first.
        let remote: [RemoteSpot]
        do {
            let vision = try await firestore.queryVisionSpots(uid: uid, token: token)
                .compactMap { SpotCodec.decode($0, origin: .vision) }
            let account = try await firestore.listUserSpots(uid: uid, token: token)
                .compactMap { SpotCodec.decode($0, origin: .account) }
            remote = vision + account
        } catch {
            print("[SpotSyncService] fetch failed (\(error)); applied nothing")
            await setState(.failed(Self.unreachable))
            return
        }

        // 3.
        let snapshot: [LocalSpot] = await MainActor.run {
            ((try? container.mainContext.fetch(FetchDescriptor<SavedLocation>())) ?? []).map { $0.toLocalSpot() }
        }
        let plan = planSync(local: snapshot, remote: remote, newId: newId)
        let nowIso = SpotCodec.iso(clock())

        // 4. Pushes: upserts, then soft deletes.
        var failed = 0
        var pushedUpserts: [LocalSpot] = []
        for spot in plan.pushUpsert {
            var linked = spot
            linked.cloudId = spot.cloudId ?? spot.id
            let fields = SpotCodec.encodeUpsert(linked, uid: uid, nowIso: nowIso)
            do {
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
        let skipped = await MainActor.run { () -> Int in
            Self.apply(upserts: toUpsert, deletes: toDelete, snapshot: snapshot, context: container.mainContext)
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
    @MainActor
    private static func apply(upserts: [LocalSpot], deletes: [String], snapshot: [LocalSpot],
                              context: ModelContext) -> Int {
        let rows = (try? context.fetch(FetchDescriptor<SavedLocation>())) ?? []
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
        try? context.save()
        return skipped
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
