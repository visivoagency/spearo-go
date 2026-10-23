package com.spearotracker.spearogo.services

import androidx.room.*
import com.spearotracker.spearogo.models.SavedLocation
import com.spearotracker.spearogo.sync.SpotStore
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao : SpotStore {
    @Query("SELECT * FROM saved_locations WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations")
    override suspend fun getAllIncludingDeleted(): List<SavedLocation>

    @Query("SELECT * FROM saved_locations WHERE isActive = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getActiveLocation(): SavedLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertAll(rows: List<SavedLocation>)

    @Delete
    suspend fun delete(location: SavedLocation)

    /** Soft delete: hides the row and queues the delete for the next sync. */
    @Query(
        "UPDATE saved_locations SET isDeleted = 1, pendingSync = 1, lastModifiedAt = :now, isActive = 0, " +
            "ownerUid = COALESCE(:ownerUid, ownerUid) WHERE id = :id"
    )
    override suspend fun markDeleted(id: String, now: Long, ownerUid: String?)

    @Query("DELETE FROM saved_locations WHERE id IN (:ids)")
    override suspend fun hardDelete(ids: List<String>)

    @Query("UPDATE saved_locations SET cloudId = :cloudId, ownerUid = :ownerUid WHERE id = :id AND cloudId IS NULL")
    override suspend fun linkCloudId(id: String, cloudId: String, ownerUid: String)

    /** The account changed to [newUid]: every other account's spots leave the watch (see [SpotStore]). */
    @Query(
        "DELETE FROM saved_locations WHERE (:includeLinked AND cloudId IS NOT NULL) " +
            "OR (ownerUid IS NOT NULL AND ownerUid != :newUid)"
    )
    override suspend fun removeAccountSpots(newUid: String, includeLinked: Boolean)

    @Query("UPDATE saved_locations SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE saved_locations SET isActive = 1 WHERE id = :locationId")
    suspend fun activateLocation(locationId: String)
}
