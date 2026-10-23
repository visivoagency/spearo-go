package com.spearotracker.spearogo.services

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: spots learn their account twin. Every column is added with a default, so no row is touched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_locations ADD COLUMN cloudId TEXT")
        db.execSQL("ALTER TABLE saved_locations ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE saved_locations ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE saved_locations ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE saved_locations ADD COLUMN origin TEXT NOT NULL DEFAULT 'GO'")
        db.execSQL("UPDATE saved_locations SET lastModifiedAt = createdAt")
    }
}
