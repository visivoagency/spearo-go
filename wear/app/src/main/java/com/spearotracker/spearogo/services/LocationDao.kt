package com.spearotracker.spearogo.services

import androidx.room.*
import com.spearotracker.spearogo.models.SavedLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY createdAt DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveLocation(): SavedLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocation)

    @Delete
    suspend fun delete(location: SavedLocation)

    @Query("UPDATE saved_locations SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE saved_locations SET isActive = 1 WHERE id = :locationId")
    suspend fun activateLocation(locationId: String)
}
