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
    /** Sets cloudId on one row only if it has none yet; touches nothing else. */
    suspend fun linkCloudId(id: String, cloudId: String)
    /** Sign-out: forget the account. Spots stay on the watch; pending deletes are dropped. */
    suspend fun detachAll()
}

/**
 * Runs [block] atomically against the store, so sync's re-read and writes can't interleave with a
 * user's edit. Wear: Room `withTransaction`; tests: a direct call.
 */
fun interface SpotTransactor {
    suspend fun run(block: suspend () -> Unit)
}
