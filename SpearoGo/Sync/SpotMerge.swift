import Foundation

// Pure two-way spot merge. Foundation only: compiled standalone by tools/spot-sync-harness.
// Line-for-line twin of wear/.../sync/SpotMerge.kt. Keep the two in lockstep.

/// Where a spot came from. VISION = top-level /spots, read-only.
enum SpotOrigin: String, Codable { case go = "GO", account = "ACCOUNT", vision = "VISION" }

struct LocalSpot: Codable, Equatable {
    var id: String
    var cloudId: String?
    var name: String
    var latitude: Double
    var longitude: Double
    var createdAt: Date
    var lastModifiedAt: Date
    var isDeleted: Bool
    var pendingSync: Bool
    var origin: SpotOrigin
}

struct RemoteSpot: Codable, Equatable {
    var docId: String
    var origin: SpotOrigin          // account or vision
    var name: String
    var latitude: Double
    var longitude: Double
    var createdAt: Date?
    var lastModifiedAt: Date?
    var isDeleted: Bool
    var camelCase: Bool             // doc's own key convention
}

struct SyncPlan {
    var upsertLocal: [LocalSpot]    // write these rows locally (pendingSync=false unless noted)
    var deleteLocal: [String]       // hard-delete these local ids
    var pushUpsert: [LocalSpot]     // PATCH users/{uid}/spots/{cloudId}
    var pushDelete: [RemoteSpot]    // soft-delete this remote doc, in its own casing
}

private let earthRadiusMetres = 6_371_000.0
private let sameSpotMetres = 100.0

private func haversineMetres(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
    let p1 = lat1 * .pi / 180
    let p2 = lat2 * .pi / 180
    let dp = (lat2 - lat1) * .pi / 180
    let dl = (lon2 - lon1) * .pi / 180
    let h = pow(sin(dp / 2), 2) + cos(p1) * cos(p2) * pow(sin(dl / 2), 2)
    return 2 * earthRadiusMetres * asin(h.squareRoot())
}

// Trim one explicit set on both platforms: code points <= U+0020, U+0085, U+00A0.
private func foldName(_ s: String) -> String {
    func trimmable(_ u: Unicode.Scalar) -> Bool { u.value <= 0x20 || u.value == 0x85 || u.value == 0xA0 }
    var scalars = Substring(s).unicodeScalars
    while let f = scalars.first, trimmable(f) { scalars.removeFirst() }
    while let l = scalars.last, trimmable(l) { scalars.removeLast() }
    return String(scalars).lowercased()
}

/// Case-insensitive trimmed name AND within 100 m.
func isSameSpot(_ a: LocalSpot, _ b: RemoteSpot) -> Bool {
    return foldName(a.name) == foldName(b.name)
        && haversineMetres(a.latitude, a.longitude, b.latitude, b.longitude) <= sameSpotMetres
}

private let epoch = Date(timeIntervalSince1970: 0)

private func refreshed(_ l: LocalSpot, _ r: RemoteSpot) -> LocalSpot {
    var u = l
    u.name = r.name
    u.latitude = r.latitude
    u.longitude = r.longitude
    u.lastModifiedAt = r.lastModifiedAt ?? l.lastModifiedAt
    u.isDeleted = false
    u.pendingSync = false
    return u
}

/// Pure two-way merge. `remote` must be complete (both collections, all pages).
func planSync(local: [LocalSpot], remote remoteAll: [RemoteSpot], newId: () -> String) -> SyncPlan {
    // Duplicate docIds: the FIRST occurrence wins (same as Kotlin).
    var remoteById: [String: RemoteSpot] = [:]
    var remote: [RemoteSpot] = []
    for r in remoteAll where remoteById[r.docId] == nil { remoteById[r.docId] = r; remote.append(r) }
    var upsertLocal: [LocalSpot] = []
    var deleteLocal: [String] = []
    var pushUpsert: [LocalSpot] = []
    var pushDelete: [RemoteSpot] = []

    // Remotes already linked by a local's cloudId, then grown as unlinked locals claim matches.
    var claimed = Set<String>()
    for l in local {
        if let c = l.cloudId, remoteById[c] != nil { claimed.insert(c) }
    }

    for l in local {
        if let cloudId = l.cloudId {
            guard let r = remoteById[cloudId] else {
                // Rule 2. VISION is never pushed: it follows the remote. A pending delete
                // whose doc is already gone is done: drop the row, never re-create the doc.
                if l.origin == .vision || l.isDeleted { deleteLocal.append(l.id) }
                else if l.pendingSync { pushUpsert.append(l) } else { deleteLocal.append(l.id) }
                continue
            }
            if l.origin == .vision {
                // VISION local: never pushed, follows the remote.
                if r.isDeleted { deleteLocal.append(l.id) } else { upsertLocal.append(refreshed(l, r)) }
            } else if l.isDeleted {
                // Rule 1: pending delete. Already deleted remotely: just drop the row.
                if r.isDeleted { deleteLocal.append(l.id) }
                else if r.origin != .vision { pushDelete.append(r) }
            } else if r.isDeleted {
                if l.pendingSync && l.lastModifiedAt > (r.lastModifiedAt ?? epoch) { pushUpsert.append(l) }
                else { deleteLocal.append(l.id) }
            } else if l.pendingSync && l.lastModifiedAt >= (r.lastModifiedAt ?? epoch) {
                pushUpsert.append(l)
            } else {
                upsertLocal.append(refreshed(l, r))
            }
        } else {
            // Rule 3
            if l.isDeleted {
                deleteLocal.append(l.id)
            } else if let match = remote.first(where: {
                !$0.isDeleted && !claimed.contains($0.docId) && isSameSpot(l, $0)
            }) {
                claimed.insert(match.docId)
                var u = l
                u.cloudId = match.docId
                u.origin = match.origin
                u.pendingSync = false
                upsertLocal.append(u)
            } else if l.origin == .vision {
                deleteLocal.append(l.id)
            } else {
                var p = l
                p.cloudId = l.id
                pushUpsert.append(p)
            }
        }
    }

    // Rule 4
    for r in remote where !r.isDeleted && !claimed.contains(r.docId) {
        let modified = r.lastModifiedAt ?? r.createdAt ?? epoch
        upsertLocal.append(LocalSpot(
            id: newId(), cloudId: r.docId, name: r.name,
            latitude: r.latitude, longitude: r.longitude,
            createdAt: r.createdAt ?? modified, lastModifiedAt: modified,
            isDeleted: false, pendingSync: false, origin: r.origin))
    }

    return SyncPlan(upsertLocal: upsertLocal, deleteLocal: deleteLocal,
                    pushUpsert: pushUpsert, pushDelete: pushDelete)
}
