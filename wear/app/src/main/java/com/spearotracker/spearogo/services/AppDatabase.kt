package com.spearotracker.spearogo.services

import androidx.room.Database
import androidx.room.RoomDatabase
import com.spearotracker.spearogo.models.SavedLocation

@Database(entities = [SavedLocation::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
}
