package com.spearotracker.spearogo.sync

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
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** Case-insensitive trimmed name AND within 100 m. */
fun isSameSpot(a: LocalSpot, b: RemoteSpot): Boolean =
    a.name.trim().equals(b.name.trim(), ignoreCase = true) &&
        haversineMetres(a.latitude, a.longitude, b.latitude, b.longitude) <= SAME_SPOT_METRES

/** Pure two-way merge. `remote` must be complete (both collections, all pages). */
fun planSync(local: List<LocalSpot>, remote: List<RemoteSpot>, newId: () -> String): SyncPlan {
    val remoteById = remote.associateBy { it.docId }
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
                // Rule 2
                if (l.pendingSync) pushUpsert.add(l) else deleteLocal.add(l.id)
            } else if (l.isDeleted) {
                // Rule 1: pending delete. VISION is read-only: neither pushed nor dropped.
                if (r.origin != SpotOrigin.VISION) pushDelete.add(r)
            } else if (r.isDeleted) {
                if (l.pendingSync && l.lastModifiedAt > (r.lastModifiedAt ?: 0L)) pushUpsert.add(l)
                else deleteLocal.add(l.id)
            } else if (l.pendingSync && l.lastModifiedAt >= (r.lastModifiedAt ?: 0L)) {
                pushUpsert.add(l)
            } else {
                upsertLocal.add(l.copy(
                    name = r.name, latitude = r.latitude, longitude = r.longitude,
                    lastModifiedAt = r.lastModifiedAt ?: l.lastModifiedAt,
                    pendingSync = false))
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
