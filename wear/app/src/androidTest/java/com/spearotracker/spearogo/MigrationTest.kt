package com.spearotracker.spearogo

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spearotracker.spearogo.models.SavedLocation
import com.spearotracker.spearogo.services.AppDatabase
import com.spearotracker.spearogo.services.MIGRATION_1_2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2KeepsSpots() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO saved_locations (id, name, latitude, longitude, createdAt, isActive) " +
                    "VALUES ('a', 'Lagos', 37.10, -8.67, 1000, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)
        db.query("SELECT * FROM saved_locations").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("a", c.getString(c.getColumnIndexOrThrow("id")))
            assertEquals("Lagos", c.getString(c.getColumnIndexOrThrow("name")))
            assertEquals(37.10, c.getDouble(c.getColumnIndexOrThrow("latitude")), 0.0)
            assertEquals(-8.67, c.getDouble(c.getColumnIndexOrThrow("longitude")), 0.0)
            assertEquals(1000L, c.getLong(c.getColumnIndexOrThrow("createdAt")))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("isActive")))
            assertEquals(1000L, c.getLong(c.getColumnIndexOrThrow("lastModifiedAt")))
            assertTrue(c.isNull(c.getColumnIndexOrThrow("cloudId")))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("isDeleted")))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("pendingSync")))
            assertEquals("GO", c.getString(c.getColumnIndexOrThrow("origin")))
        }
    }

    @Test
    fun migratedDatabaseOpensThroughRoomAndListsTheSpot() = runBlocking {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO saved_locations (id, name, latitude, longitude, createdAt, isActive) " +
                    "VALUES ('a', 'Lagos', 37.10, -8.67, 1000, 1)"
            )
            close()
        }
        val room = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbName
        ).addMigrations(MIGRATION_1_2).build()
        try {
            val all = room.locationDao().getAllLocations().first()
            assertEquals(1, all.size)
            assertEquals("Lagos", all[0].name)
            assertTrue(all[0].isActive)
            assertEquals(1000L, all[0].lastModifiedAt)
            assertEquals("a", room.locationDao().getActiveLocation()?.id)
        } finally {
            room.close()
        }
    }

    /**
     * removeLinked runs when a DIFFERENT account signs in (sign-out keeps links): the old account's
     * spots (every row with a cloudId, soft-deleted included) leave; never-synced spots stay.
     */
    @Test
    fun accountChangeRemovesOldAccountSpots() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        try {
            val dao = room.locationDao()
            dao.insert(
                SavedLocation(id = "linked", name = "Lagos", latitude = 37.1, longitude = -8.67,
                    createdAt = 1, isActive = true, cloudId = "doc1", pendingSync = true, origin = "ACCOUNT")
            )
            dao.insert(
                SavedLocation(id = "tombstone", name = "Sagres", latitude = 37.0, longitude = -8.9,
                    createdAt = 2, cloudId = "doc2", isDeleted = true, pendingSync = true)
            )
            dao.insert(
                SavedLocation(id = "local", name = "Faro", latitude = 37.0, longitude = -7.9,
                    createdAt = 3, pendingSync = true)
            )

            dao.removeLinked()

            val rows = dao.getAllIncludingDeleted()
            assertEquals(1, rows.size)
            assertEquals("local", rows[0].id)
            assertNull(rows[0].cloudId)
            assertTrue(rows[0].pendingSync)          // still queued for the new account
            assertNull(dao.getActiveLocation())      // the active spot left: "Use my location"
        } finally {
            room.close()
        }
    }

    @Test
    fun markDeletedHidesTheSpotAndQueuesTheDelete() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        try {
            val dao = room.locationDao()
            dao.insert(SavedLocation(id = "a", name = "Lagos", latitude = 37.1, longitude = -8.67,
                createdAt = 1, isActive = true))
            dao.markDeleted("a", 5000)

            assertTrue(dao.getAllLocations().first().isEmpty())
            assertNull(dao.getActiveLocation())
            val row = dao.getAllIncludingDeleted().single()
            assertTrue(row.isDeleted); assertTrue(row.pendingSync)
            assertEquals(5000L, row.lastModifiedAt); assertFalse(row.isActive)

            dao.hardDelete(listOf("a"))
            assertTrue(dao.getAllIncludingDeleted().isEmpty())
        } finally {
            room.close()
        }
    }
}
