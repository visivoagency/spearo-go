package com.spearotracker.spearogo.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
    /** Firestore doc id of this spot's account twin; null until it has one. */
    val cloudId: String? = null,
    val lastModifiedAt: Long = createdAt,
    /** Soft delete: the row waits here until the delete has reached the account. */
    val isDeleted: Boolean = false,
    val pendingSync: Boolean = false,
    /** A SpotOrigin enum name: "GO", "ACCOUNT" or "VISION". */
    val origin: String = "GO"
)
