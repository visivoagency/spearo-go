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
    val isActive: Boolean = false
)
