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
     * The account changed to [newUid]: remove every spot that belongs to another account. That is
     * each row whose ownerUid is set and is not [newUid], plus, when [includeLinked] (the links
     * belonged to another account), every row with a cloudId. Soft-deleted rows included.
     * Rows with no cloudId and no ownerUid (saved while signed out) stay.
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
