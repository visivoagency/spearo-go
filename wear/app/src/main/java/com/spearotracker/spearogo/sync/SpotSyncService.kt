package com.spearotracker.spearogo.sync

import android.content.Context
import android.util.Log
import com.spearotracker.spearogo.models.SavedLocation
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

/**
 * Sync bookkeeping kept apart from the auth store: when `users/{uid}.apps.go.last_seen_at` was
 * last written, and which account the spots' cloudIds belong to.
 */
interface SyncMarkerStore {
    fun lastWrittenAt(): Long?
    fun setLastWrittenAt(ms: Long)
    /** Forgets the marker time only. [linkedUid] survives sign-out, as the links do. */
    fun clear()
    /** The uid whose `users/{uid}/spots` docs the local cloudIds point at; null before any sync. */
    fun linkedUid(): String?
    fun setLinkedUid(uid: String)
}

/**
 * SharedPreferences "sync", MODE_PRIVATE. Holds a timestamp and the linked uid. Backed up with
 * the spots database (only "auth" is excluded), so a restore keeps the two consistent: restored
 * cloudIds always come with the account they belong to.
 */
class SharedPrefsSyncMarkerStore(context: Context) : SyncMarkerStore {
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
    override fun lastWrittenAt(): Long? = if (prefs.contains(KEY)) prefs.getLong(KEY, 0L) else null
    override fun setLastWrittenAt(ms: Long) { prefs.edit().putLong(KEY, ms).apply() }
    override fun clear() { prefs.edit().remove(KEY).apply() }
    override fun linkedUid(): String? = prefs.getString(LINKED_UID, null)
    override fun setLinkedUid(uid: String) {
        // Synchronous: the links written next must never outlive a lost uid.
        if (!prefs.edit().putString(LINKED_UID, uid).commit()) throw IllegalStateException("sync prefs write failed")
    }
    private companion object {
        const val KEY = "marker_written_at"
        const val LINKED_UID = "linked_uid"
    }
}

/**
 * Two-way spot sync with the Spearo account. Twin of SpearoGo/Sync/SpotSyncService.swift.
 *
 * sync(), one at a time ([Mutex]), never throws (cancellation aside):
 *  1. Token; none and no session -> [SyncState.SignedOut], no requests.
 *  2. Fetch Vision's top-level `spots` FIRST, then `users/{uid}/spots` (first docId wins in
 *     the merge, so a Vision-owned doc stays read-only). Either fetch fails -> Failed, and
 *     NOTHING is applied: a partial remote list would make merge rule 2 delete spots.
 *  3. [planSync], minus rows linked to a fetched doc that doesn't decode (left untouched).
 *     A pushUpsert aimed at an existing camelCase ACCOUNT doc is not pushed
 *     (see [withoutCamelCasePushes]); the row follows the remote instead.
 *  4. Push upserts, then soft deletes.
 *  5. Apply locally: upsertLocal (keeping each row's isActive), deleteLocal, then the
 *     successful pushes (pendingSync=false + cloudId persisted; pushed deletes hard-deleted).
 *     All of step 5 runs in one transaction ([SpotTransactor]). A row the user changed while
 *     the sync was on the wire is left for the next sync, except that a pushed row still
 *     without a cloudId is linked to the doc it created (else the doc would be orphaned and
 *     come back as a new spot).
 *  6. Failed pushes stay pending; state Failed("Couldn't upload N spots").
 * The account marker is written on sign-in and at most once per 24 h after that, only ever onto an
 * existing `users/{uid}` (never creating it); if there is none yet it is retried on the next sync.
 *
 * Links are scoped to an account ([SyncMarkerStore.linkedUid]). Sign-out (planned, or a session a
 * refresh 400 cleared) keeps every row and cloudId, so the same account signing back in finds its
 * spots linked and nothing is duplicated. Spots stay with the account they were saved to (owner
 * ruling, 23 Oct): when a DIFFERENT account syncs, [SpotStore.removeAccountSpots] runs first. The
 * other account's synced spots leave the watch (they are safe in it and come back when it signs
 * in again); its not-yet-uploaded ones stay, hidden ([viewerUid]) and left out of every merge,
 * until that account signs back in and they upload to it. Rows saved while signed out (no
 * cloudId, no ownerUid) stay and upload to the new one. An active spot removed or hidden this way
 * loses its isActive, so the app falls back to "Use my location".
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
    private val debugLog: (String) -> Unit = { Log.d(TAG, it) },
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(
        if (auth.session() != null) SyncState.Idle else SyncState.SignedOut
    )
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _viewerUid = MutableStateFlow(markers.linkedUid())
    /**
     * The account whose spots the watch shows ([SyncMarkerStore.linkedUid]; it survives sign-out).
     * Rows owned by any other account are hidden. Null until the first sign-in.
     */
    val viewerUid: StateFlow<String?> = _viewerUid.asStateFlow()

    suspend fun sync() {
        try {
            mutex.withLock { runSync() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("sync failed (${e.javaClass.simpleName})")
            _state.value = SyncState.Failed(STORE_FAILED)
        }
    }

    /**
     * Right after a successful sign-in: remove the old account's spots if they are linked to
     * another account, the marker (always), then a first sync. The state is [SyncState.Syncing]
     * from the start, so the sign-in row can't be tapped again meanwhile. If the removal fails the
     * sign-in is undone (state Failed): syncing on with the old account's links would misfile spots.
     */
    suspend fun onSignedIn(session: Session) {
        val proceed = try {
            // Under the lock, so it can't race a sign-out; skipped if one already happened.
            mutex.withLock {
                if (auth.session()?.uid != session.uid) {
                    if (auth.session() == null) _state.value = SyncState.SignedOut
                    return@withLock false
                }
                _state.value = SyncState.Syncing
                if (!linkTo(session.uid)) {
                    auth.signOut()
                    _state.value = SyncState.Failed(STORE_FAILED)
                    return@withLock false
                }
                writeMarker(session.uid, session.idToken)
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("marker failed (${e.javaClass.simpleName})")
            true
        }
        if (proceed) sync()
    }

    /**
     * Forget the session. Every spot keeps its row and its link (see the class comment); only the
     * auth store and the marker time are cleared.
     */
    suspend fun signOut() {
        auth.signOut()
        mutex.withLock { markers.clear() }
        _state.value = SyncState.SignedOut
    }

    /**
     * Saves a new spot the user made. Signed in, it belongs to that account (ownerUid), even
     * before it is pushed; signed out, it belongs to none and uploads to whichever account signs
     * in next.
     */
    suspend fun addSpot(row: SavedLocation) {
        store.upsertAll(listOf(row.copy(ownerUid = ownerForEdits())))
    }

    /**
     * The account a spot made or deleted now belongs to: the session's, unless the links still
     * belong to another account (signed in, but [linkTo] hasn't run yet). Then none: the row stays
     * visible and goes to the session's account once it links, instead of being hidden as a
     * stranger's.
     */
    private fun ownerForEdits(): String? {
        val uid = auth.session()?.uid ?: return null
        val linked = markers.linkedUid()
        return if (linked == null || linked == uid) uid else null
    }

    /**
     * The user removed a spot. Signed out and never linked: it is simply gone. Otherwise it is
     * soft-deleted and waits for its account (pushed when that account syncs, kept hidden for it
     * if another one signs in first). Signed in, the row is stamped with
     * that account, and a Vision-owned spot is read-only, so nothing happens.
     */
    suspend fun deleteSpot(id: String) {
        val row = store.getAllIncludingDeleted().firstOrNull { it.id == id } ?: return
        val uid = auth.session()?.uid
        val signedIn = uid != null
        if (signedIn && row.origin == SpotOrigin.VISION.name) return
        if (signedIn || row.cloudId != null) store.markDeleted(id, clock(), ownerForEdits())
        else store.hardDelete(listOf(id))
    }

    /**
     * Makes the local links belong to [uid]: any other account's spots are removed first
     * ([SpotStore.removeAccountSpots]): rows owned by another uid, and, if the links belonged to
     * another account, every linked row. Call under [mutex]. False if the removal (or recording
     * the uid) failed: the caller must not sync.
     */
    private suspend fun linkTo(uid: String): Boolean {
        val linked = markers.linkedUid()
        if (linked == uid) return true
        return try {
            store.removeAccountSpots(uid, includeLinked = linked != null)
            markers.setLinkedUid(uid)
            _viewerUid.value = uid
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("account switch: removing old spots failed (${e.javaClass.simpleName})")
            false
        }
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
        // The session's account may differ from the links' (e.g. the app died between sign-in
        // and onSignedIn): remove the old account's spots first, or fail without touching anything.
        if (!linkTo(uid)) {
            _state.value = SyncState.Failed(STORE_FAILED)
            return
        }

        // 2. Both collections, complete, or nothing.
        val fetchedIds = HashSet<String>()
        val remote = try {
            val visionDocs = firestore.queryVisionSpots(uid, token)
            val accountDocs = firestore.listUserSpots(uid, token)
            (visionDocs + accountDocs).forEach { d -> SpotCodec.docId(d)?.let(fetchedIds::add) }
            visionDocs.mapNotNull { SpotCodec.decode(it, SpotOrigin.VISION) } +
                accountDocs.mapNotNull { SpotCodec.decode(it, SpotOrigin.ACCOUNT) }
        } catch (e: FirestoreException) {
            log("fetch failed (${e.message}); applied nothing")
            _state.value = SyncState.Failed(UNREACHABLE)
            return
        }

        // 3. A row linked to a doc that exists but doesn't decode (no name/latitude/longitude) is
        // left out of the merge entirely: it isn't gone (rule 2 would delete it), and we must not
        // push over a doc we can't read.
        val undecodable = fetchedIds - remote.map { it.docId }.toSet()
        if (undecodable.isNotEmpty()) log("${undecodable.size} spot docs unreadable; their spots left as they are")
        val snapshot = store.getAllIncludingDeleted()
        // Another account's hidden spots are never merged into, pushed to or deleted from this one.
        val mergeable = snapshot.filter {
            (it.ownerUid == null || it.ownerUid == uid) && (it.cloudId == null || it.cloudId !in undecodable)
        }
        val plan = withoutCamelCasePushes(planSync(mergeable.map { it.toLocalSpot() }, remote, newId), remote)
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
        val before = mergeable.associateBy { it.id }
        val upserts = mutableListOf<LocalSpot>()
        val deletes = mutableListOf<String>()
        upserts += plan.upsertLocal
        deletes += plan.deleteLocal
        for (s in pushedUpserts) if (s.isDeleted) deletes += s.id else upserts += s
        deletes += mergeable.filter { it.isDeleted && it.cloudId in pushedDeleteDocIds }.map { it.id }
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
                store.upsertAll(keptUpserts.map {
                    it.toSavedLocation(isActive = now[it.id]?.isActive ?: false, ownerUid = uid)
                })
            }
            if (keptDeletes.isNotEmpty()) store.hardDelete(keptDeletes)
            // Pushed, then edited meanwhile: still record which doc it created (only if unlinked).
            for ((id, cloudId) in pushedIds) {
                if (!untouched(id) && now[id] != null) store.linkCloudId(id, cloudId, uid)
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
            firestore.patchMarker(uid, DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(at)), token)
            markers.setLastWrittenAt(at)
        } catch (e: FirestoreException) {
            // 404 / 400 (FAILED_PRECONDITION): no users/{uid} yet; the website creates it. Not an
            // error. Either way the time isn't recorded, so the next sync tries again.
            if (e.code == 404 || e.code == 400) debugLog("marker skipped (HTTP ${e.code})")
            else log("marker failed (${e.message})")
        }
    }

    private companion object {
        const val TAG = "SpotSyncService"
        const val UNREACHABLE = "Couldn't reach your account"
        const val STORE_FAILED = "Sync failed. Try again."
        const val MARKER_INTERVAL_MS = 24 * 60 * 60_000L
    }
}
