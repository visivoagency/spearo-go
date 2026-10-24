package com.spearotracker.spearogo.sync

import com.spearotracker.spearogo.models.SavedLocation

/**
 * The local-store calls [SpotSyncService] makes. The Room [com.spearotracker.spearogo.services.LocationDao]
 * implements it; tests use an in-memory fake.
 */
interface SpotStore {
    suspend fun getAllIncludingDeleted(): List<SavedLocation>
    suspend fun upsertAll(rows: List<SavedLocation>)
    suspend fun hardDelete(ids: List<String>)
    /** Sets cloudId (and ownerUid) on one row only if it has no cloudId yet; touches nothing else. */
    suspend fun linkCloudId(id: String, cloudId: String, ownerUid: String)
    /** Soft delete: hides the row and queues the delete for the next sync. A non-null [ownerUid] is stamped on it. */
    suspend fun markDeleted(id: String, now: Long, ownerUid: String?)
    /**
     * The account changed to [newUid]. Spots stay with the account they were saved to (owner
     * ruling, 23 Oct), so for each row owned by another uid:
     *  - synced (pendingSync false): removed. It is safe in that account and comes back when it
     *    signs in again;
     *  - not yet uploaded (pendingSync true, a new spot or a delete): kept but hidden and made
     *    inactive. It is never shown to or uploaded to [newUid], and uploads to its own account
     *    when that account signs back in.
     * When [includeLinked] (the links belonged to another account), linked rows with no owner
     * (written before owners were recorded) are removed too. Rows saved while signed out (no
     * cloudId, no ownerUid) stay and upload to the new account. Rows owned by [newUid] are untouched.
     */
    suspend fun removeAccountSpots(newUid: String, includeLinked: Boolean)
}

/**
 * Runs [block] atomically against the store, so sync's re-read and writes can't interleave with a
 * user's edit. Wear: Room `withTransaction`; tests: a direct call.
 */
fun interface SpotTransactor {
    suspend fun run(block: suspend () -> Unit)
}
