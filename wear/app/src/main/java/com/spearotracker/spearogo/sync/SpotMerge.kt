package com.spearotracker.spearogo.sync

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a spot came from. VISION = top-level /spots, read-only. */
enum class SpotOrigin { GO, ACCOUNT, VISION }

data class LocalSpot(
    val id: String, val cloudId: String?, val name: String,
    val latitude: Double, val longitude: Double,
    val createdAt: Long, val lastModifiedAt: Long,
    val isDeleted: Boolean, val pendingSync: Boolean, val origin: SpotOrigin)

data class RemoteSpot(
    val docId: String, val origin: SpotOrigin,          // ACCOUNT or VISION
    val name: String, val latitude: Double, val longitude: Double,
    val createdAt: Long?, val lastModifiedAt: Long?, val isDeleted: Boolean,
    val camelCase: Boolean)                             // doc's own key convention

data class SyncPlan(
    val upsertLocal: List<LocalSpot>,       // write these rows locally (pendingSync=false unless noted)
    val deleteLocal: List<String>,          // hard-delete these local ids
    val pushUpsert: List<LocalSpot>,        // PATCH users/{uid}/spots/{cloudId}
    val pushDelete: List<RemoteSpot>)       // soft-delete this remote doc, in its own casing

private const val EARTH_RADIUS_M = 6_371_000.0
private const val SAME_SPOT_METRES = 100.0

private fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = lat1 * PI / 180
    val p2 = lat2 * PI / 180
    val dp = (lat2 - lat1) * PI / 180
    val dl = (lon2 - lon1) * PI / 180
    val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

// Trim one explicit set on both platforms: code points <= U+0020, U+0085, U+00A0.
private fun foldName(s: String): String =
    s.trim { it.code <= 0x20 || it.code == 0x85 || it.code == 0xA0 }.lowercase()

/** Case-insensitive trimmed name AND within 100 m. */
fun isSameSpot(a: LocalSpot, b: RemoteSpot): Boolean =
    foldName(a.name) == foldName(b.name) &&
        haversineMetres(a.latitude, a.longitude, b.latitude, b.longitude) <= SAME_SPOT_METRES

private fun refreshed(l: LocalSpot, r: RemoteSpot): LocalSpot = l.copy(
    name = r.name, latitude = r.latitude, longitude = r.longitude,
    lastModifiedAt = r.lastModifiedAt ?: l.lastModifiedAt,
    isDeleted = false, pendingSync = false)

/** Pure two-way merge. `remote` must be complete (both collections, all pages). */
fun planSync(local: List<LocalSpot>, remoteAll: List<RemoteSpot>, newId: () -> String): SyncPlan {
    // Duplicate docIds: the FIRST occurrence wins (same as Swift).
    val remoteById = LinkedHashMap<String, RemoteSpot>()
    for (r in remoteAll) remoteById.putIfAbsent(r.docId, r)
    val remote = remoteById.values.toList()
    val upsertLocal = mutableListOf<LocalSpot>()
    val deleteLocal = mutableListOf<String>()
    val pushUpsert = mutableListOf<LocalSpot>()
    val pushDelete = mutableListOf<RemoteSpot>()

    // Remotes already linked by a local's cloudId, then grown as unlinked locals claim matches.
    val claimed = HashSet<String>()
    for (l in local) {
        val c = l.cloudId
        if (c != null && remoteById.containsKey(c)) claimed.add(c)
    }

    for (l in local) {
        val cloudId = l.cloudId
        if (cloudId != null) {
            val r = remoteById[cloudId]
            if (r == null) {
                // Rule 2. VISION is never pushed: it follows the remote.
                if (l.origin == SpotOrigin.VISION) deleteLocal.add(l.id)
                else if (l.pendingSync) pushUpsert.add(l) else deleteLocal.add(l.id)
            } else if (l.origin == SpotOrigin.VISION) {
                // VISION local: never pushed, follows the remote.
                if (r.isDeleted) deleteLocal.add(l.id) else upsertLocal.add(refreshed(l, r))
            } else if (l.isDeleted) {
                // Rule 1: pending delete. Already deleted remotely: just drop the row.
                if (r.isDeleted) deleteLocal.add(l.id)
                else if (r.origin != SpotOrigin.VISION) pushDelete.add(r)
            } else if (r.isDeleted) {
                if (l.pendingSync && l.lastModifiedAt > (r.lastModifiedAt ?: 0L)) pushUpsert.add(l)
                else deleteLocal.add(l.id)
            } else if (l.pendingSync && l.lastModifiedAt >= (r.lastModifiedAt ?: 0L)) {
                pushUpsert.add(l)
            } else {
                upsertLocal.add(refreshed(l, r))
            }
        } else {
            // Rule 3
            if (l.isDeleted) {
                deleteLocal.add(l.id)
            } else {
                val match = remote.firstOrNull {
                    !it.isDeleted && !claimed.contains(it.docId) && isSameSpot(l, it)
                }
                if (match != null) {
                    claimed.add(match.docId)
                    upsertLocal.add(l.copy(cloudId = match.docId, origin = match.origin, pendingSync = false))
                } else if (l.origin == SpotOrigin.VISION) {
                    deleteLocal.add(l.id)
                } else {
                    pushUpsert.add(l.copy(cloudId = l.id))
                }
            }
        }
    }

    // Rule 4
    for (r in remote) {
        if (r.isDeleted || claimed.contains(r.docId)) continue
        val modified = r.lastModifiedAt ?: r.createdAt ?: 0L
        upsertLocal.add(LocalSpot(
            id = newId(), cloudId = r.docId, name = r.name,
            latitude = r.latitude, longitude = r.longitude,
            createdAt = r.createdAt ?: modified, lastModifiedAt = modified,
            isDeleted = false, pendingSync = false, origin = r.origin))
    }

    return SyncPlan(upsertLocal, deleteLocal, pushUpsert, pushDelete)
}
