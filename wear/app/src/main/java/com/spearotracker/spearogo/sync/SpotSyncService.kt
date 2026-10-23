package com.spearotracker.spearogo.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed interface SyncState {
    data object SignedOut : SyncState
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Synced(val at: Long) : SyncState
    data class Failed(val message: String) : SyncState
}

/** When `users/{uid}.apps.go.last_seen_at` was last written. Kept apart from the auth store. */
interface SyncMarkerStore {
    fun lastWrittenAt(): Long?
    fun setLastWrittenAt(ms: Long)
    fun clear()
}

/** SharedPreferences "sync", MODE_PRIVATE. Holds a timestamp only. */
class SharedPrefsSyncMarkerStore(context: Context) : SyncMarkerStore {
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
    override fun lastWrittenAt(): Long? = if (prefs.contains(KEY)) prefs.getLong(KEY, 0L) else null
    override fun setLastWrittenAt(ms: Long) { prefs.edit().putLong(KEY, ms).apply() }
    override fun clear() { prefs.edit().remove(KEY).apply() }
    private companion object { const val KEY = "marker_written_at" }
}

/**
 * Two-way spot sync with the Spearo account. Twin of SpearoGo/Sync/SpotSyncService.swift.
 *
 * sync(), one at a time ([Mutex]), never throws (cancellation aside):
 *  1. Token; none and no session -> [SyncState.SignedOut], no requests.
 *  2. Fetch Vision's top-level `spots` FIRST, then `users/{uid}/spots` (first docId wins in
 *     the merge, so a Vision-owned doc stays read-only). Either fetch fails -> Failed, and
 *     NOTHING is applied: a partial remote list would make merge rule 2 delete spots.
 *  3. [planSync]. A pushUpsert aimed at an existing camelCase ACCOUNT doc is not pushed
 *     (see [withoutCamelCasePushes]); the row follows the remote instead.
 *  4. Push upserts, then soft deletes.
 *  5. Apply locally: upsertLocal (keeping each row's isActive), deleteLocal, then the
 *     successful pushes (pendingSync=false + cloudId persisted; pushed deletes hard-deleted).
 *     All of step 5 runs in one transaction ([SpotTransactor]). A row the user changed while
 *     the sync was on the wire is left for the next sync, except that a pushed row still
 *     without a cloudId is linked to the doc it created (else the doc would be orphaned and
 *     come back as a new spot).
 *  6. Failed pushes stay pending; state Failed("Couldn't upload N spots").
 * The account marker is written on sign-in and at most once per 24 h after that.
 * Logs carry counts and status codes only: no uid, email, names, coordinates or tokens.
 */
class SpotSyncService(
    private val auth: AuthClient,
    private val firestore: FirestoreClient,
    private val store: SpotStore,
    private val markers: SyncMarkerStore,
    /** Makes apply atomic. Wear: `{ db.withTransaction(it) }`. */
    private val transactor: SpotTransactor,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(
        if (auth.session() != null) SyncState.Idle else SyncState.SignedOut
    )
    val state: StateFlow<SyncState> = _state.asStateFlow()

    suspend fun sync() {
        try {
            mutex.withLock { runSync() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("sync failed (${e.javaClass.simpleName})")
            _state.value = SyncState.Failed("Sync failed. Try again.")
        }
    }

    /** Right after a successful sign-in: the marker (always), then a first sync. */
    suspend fun onSignedIn(session: Session) {
        try {
            // Under the lock, so it can't race a sign-out; skipped if one already happened.
            mutex.withLock {
                if (auth.session()?.uid == session.uid) writeMarker(session.uid, session.idToken)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("marker failed (${e.javaClass.simpleName})")
        }
        sync()
    }

    /** Forget the account. Spots stay on the watch; links and pending deletes go. */
    suspend fun signOut() {
        auth.signOut()
        try {
            mutex.withLock {
                store.detachAll()
                markers.clear()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("detach failed (${e.javaClass.simpleName})")
        }
        _state.value = SyncState.SignedOut
    }

    private suspend fun runSync() {
        val token = auth.currentIdToken()
        val uid = auth.session()?.uid
        if (token == null || uid == null) {
            _state.value = if (auth.session() == null) SyncState.SignedOut
            else SyncState.Failed(UNREACHABLE)   // refresh failed but the session is kept
            return
        }
        _state.value = SyncState.Syncing

        // 2. Both collections, complete, or nothing.
        val remote = try {
            val vision = firestore.queryVisionSpots(uid, token).mapNotNull { SpotCodec.decode(it, SpotOrigin.VISION) }
            val account = firestore.listUserSpots(uid, token).mapNotNull { SpotCodec.decode(it, SpotOrigin.ACCOUNT) }
            vision + account
        } catch (e: FirestoreException) {
            log("fetch failed (${e.message}); applied nothing")
            _state.value = SyncState.Failed(UNREACHABLE)
            return
        }

        // 3.
        val snapshot = store.getAllIncludingDeleted()
        val plan = withoutCamelCasePushes(planSync(snapshot.map { it.toLocalSpot() }, remote, newId), remote)
        val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(clock()))

        // 4. Pushes: upserts, then soft deletes.
        var failed = 0
        val pushedUpserts = mutableListOf<LocalSpot>()
        for (spot in plan.pushUpsert) {
            val linked = spot.copy(cloudId = spot.cloudId ?: spot.id)
            val fields = SpotCodec.encodeUpsert(linked, uid, nowIso)
            try {
                // Masked to Go's own 12 snake_case keys, so a website doc keeps its other fields
                // (notes, photos...). It is never a camelCase doc: withoutCamelCasePushes dropped
                // those, since adding snake keys beside camel ones would leave the camel
                // isDeleted/lastModifiedAt that Tracker's restore reads stale.
                firestore.patch("users/$uid/spots/${linked.cloudId}", fields, fields.keySet().toList(), token)
                pushedUpserts.add(linked.copy(pendingSync = false))
            } catch (e: FirestoreException) {
                failed++
                log("upload failed (${e.message})")
            }
        }
        val pushedDeleteDocIds = mutableSetOf<String>()
        for (r in plan.pushDelete) {
            val (fields, mask) = SpotCodec.encodeSoftDelete(r, nowIso)
            try {
                firestore.patch("users/$uid/spots/${r.docId}", fields, mask, token)
                pushedDeleteDocIds.add(r.docId)
            } catch (e: FirestoreException) {
                failed++
                log("delete upload failed (${e.message})")
            }
        }

        // 5. Apply. Signed out (or switched account) meanwhile: apply nothing.
        if (auth.session()?.uid != uid) {
            _state.value = if (auth.session() == null) SyncState.SignedOut else SyncState.Idle
            return
        }
        val before = snapshot.associateBy { it.id }
        val upserts = mutableListOf<LocalSpot>()
        val deletes = mutableListOf<String>()
        upserts += plan.upsertLocal
        deletes += plan.deleteLocal
        for (s in pushedUpserts) if (s.isDeleted) deletes += s.id else upserts += s
        deletes += snapshot.filter { it.isDeleted && it.cloudId in pushedDeleteDocIds }.map { it.id }
        val pushedIds = pushedUpserts.associate { it.id to it.cloudId!! }

        var skipped = 0
        transactor.run {
            val now = store.getAllIncludingDeleted().associateBy { it.id }
            // A row edited, added or removed by the user since the snapshot waits for the next sync.
            fun untouched(id: String): Boolean {
                val was = before[id] ?: return now[id] == null
                return now[id]?.copy(isActive = was.isActive) == was
            }
            val keptUpserts = upserts.filter { untouched(it.id) }
            val keptDeletes = deletes.distinct().filter { untouched(it) }
            skipped = upserts.size + deletes.distinct().size - keptUpserts.size - keptDeletes.size
            if (keptUpserts.isNotEmpty()) {
                store.upsertAll(keptUpserts.map { it.toSavedLocation(isActive = now[it.id]?.isActive ?: false) })
            }
            if (keptDeletes.isNotEmpty()) store.hardDelete(keptDeletes)
            // Pushed, then edited meanwhile: still record which doc it created (only if unlinked).
            for ((id, cloudId) in pushedIds) {
                if (!untouched(id) && now[id] != null) store.linkCloudId(id, cloudId)
            }
        }
        if (skipped > 0) log("$skipped spots changed during sync; left for next time")

        // Marker: best effort, at most once per 24 h.
        val last = markers.lastWrittenAt()
        if (last == null || clock() - last >= MARKER_INTERVAL_MS) writeMarker(uid, token)

        // 6.
        _state.value = if (failed > 0) {
            SyncState.Failed("Couldn't upload $failed ${if (failed == 1) "spot" else "spots"}")
        } else {
            SyncState.Synced(clock())
        }
    }

    /**
     * Ruling: a pushUpsert never targets an existing camelCase ACCOUNT doc. Such a spot follows
     * the remote instead, as merge rule 1's else-branch would: refreshed if the doc is live,
     * dropped locally if the doc is deleted.
     */
    private fun withoutCamelCasePushes(plan: SyncPlan, remote: List<RemoteSpot>): SyncPlan {
        val byId = LinkedHashMap<String, RemoteSpot>()
        for (r in remote) byId.putIfAbsent(r.docId, r)   // first wins, as in planSync
        val upsertLocal = plan.upsertLocal.toMutableList()
        val deleteLocal = plan.deleteLocal.toMutableList()
        val pushUpsert = mutableListOf<LocalSpot>()
        for (l in plan.pushUpsert) {
            val r = l.cloudId?.let { byId[it] }
            if (r == null || r.origin != SpotOrigin.ACCOUNT || !r.camelCase) { pushUpsert += l; continue }
            if (r.isDeleted) deleteLocal += l.id
            else upsertLocal += l.copy(
                name = r.name, latitude = r.latitude, longitude = r.longitude,
                lastModifiedAt = r.lastModifiedAt ?: l.lastModifiedAt, isDeleted = false, pendingSync = false)
        }
        return plan.copy(upsertLocal = upsertLocal, deleteLocal = deleteLocal, pushUpsert = pushUpsert)
    }

    private suspend fun writeMarker(uid: String, token: String) {
        val at = clock()
        try {
            val iso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(at))
            firestore.patch("users/$uid", FirestoreClient.markerFields(iso), listOf(FirestoreClient.MARKER_PATH), token)
            markers.setLastWrittenAt(at)
        } catch (e: FirestoreException) {
            log("marker failed (${e.message})")
        }
    }

    private companion object {
        const val TAG = "SpotSyncService"
        const val UNREACHABLE = "Couldn't reach your account"
        const val MARKER_INTERVAL_MS = 24 * 60 * 60_000L
    }
}
