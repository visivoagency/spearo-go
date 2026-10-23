package com.spearotracker.spearogo.services

import androidx.room.*
import com.spearotracker.spearogo.models.SavedLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM saved_locations WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations")
    suspend fun getAllIncludingDeleted(): List<SavedLocation>

    @Query("SELECT * FROM saved_locations WHERE isActive = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getActiveLocation(): SavedLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SavedLocation>)

    @Delete
    suspend fun delete(location: SavedLocation)

    /** Soft delete: hides the row and queues the delete for the next sync. */
    @Query("UPDATE saved_locations SET isDeleted = 1, pendingSync = 1, lastModifiedAt = :now, isActive = 0 WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("DELETE FROM saved_locations WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<String>)

    @Query("DELETE FROM saved_locations WHERE isDeleted = 1")
    suspend fun deleteSoftDeleted()

    @Query("UPDATE saved_locations SET cloudId = NULL, pendingSync = 0")
    suspend fun clearCloudLinks()

    /** Sign-out: forget the account. Spots stay on the watch; pending deletes are dropped. */
    @Transaction
    suspend fun detachAll() {
        deleteSoftDeleted()
        clearCloudLinks()
    }

    @Query("UPDATE saved_locations SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE saved_locations SET isActive = 1 WHERE id = :locationId")
    suspend fun activateLocation(locationId: String)
}
