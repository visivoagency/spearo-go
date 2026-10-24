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
import com.spearotracker.spearogo.services.MIGRATION_2_3
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
    fun migrate2To3KeepsSpots() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO saved_locations (id, name, latitude, longitude, createdAt, isActive, cloudId, " +
                    "lastModifiedAt, isDeleted, pendingSync, origin) " +
                    "VALUES ('a', 'Lagos', 37.10, -8.67, 1000, 1, 'doc1', 2000, 0, 1, 'ACCOUNT')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)
        db.query("SELECT * FROM saved_locations").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("a", c.getString(c.getColumnIndexOrThrow("id")))
            assertEquals("Lagos", c.getString(c.getColumnIndexOrThrow("name")))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("isActive")))
            assertEquals("doc1", c.getString(c.getColumnIndexOrThrow("cloudId")))
            assertEquals(2000L, c.getLong(c.getColumnIndexOrThrow("lastModifiedAt")))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("pendingSync")))
            assertEquals("ACCOUNT", c.getString(c.getColumnIndexOrThrow("origin")))
            assertTrue(c.isNull(c.getColumnIndexOrThrow("ownerUid")))
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            val all = room.locationDao().getVisibleLocations(null).first()
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
     * removeAccountSpots runs when a DIFFERENT account signs in (sign-out keeps links): the old
     * account's synced spots and unowned linked rows leave; its unsynced ones stay, hidden from the
     * new account's list and inactive; spots saved while signed out stay.
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
                SavedLocation(id = "unpushed", name = "Albufeira", latitude = 37.1, longitude = -8.2,
                    createdAt = 3, isActive = true, pendingSync = true, ownerUid = "U1")
            )
            dao.insert(
                SavedLocation(id = "synced", name = "Lagoa", latitude = 37.1, longitude = -8.4,
                    createdAt = 6, cloudId = "doc6", ownerUid = "U1")
            )
            dao.insert(
                SavedLocation(id = "mine", name = "Tavira", latitude = 37.1, longitude = -7.6,
                    createdAt = 4, pendingSync = true, ownerUid = "U2")
            )
            dao.insert(
                SavedLocation(id = "local", name = "Faro", latitude = 37.0, longitude = -7.9,
                    createdAt = 5, pendingSync = true)
            )

            dao.removeAccountSpots("U2", includeLinked = true)

            val rows = dao.getAllIncludingDeleted().associateBy { it.id }
            assertEquals(setOf("mine", "local", "unpushed"), rows.keys)
            assertTrue(rows.getValue("local").pendingSync)   // still queued for the new account
            assertFalse(rows.getValue("unpushed").isActive)  // kept for U1, but not active
            assertNull(dao.getActiveLocation())              // the active spots went: "Use my location"
            assertEquals(setOf("mine", "local"), dao.getVisibleLocations("U2").first().map { it.id }.toSet())
            assertEquals(setOf("unpushed", "local"), dao.getVisibleLocations("U1").first().map { it.id }.toSet())
        } finally {
            room.close()
        }
    }

    /** First sign-in (no links yet): linked rows are kept; only another uid's owned rows go. */
    @Test
    fun firstSignInRemovesOnlyOtherOwners() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        try {
            val dao = room.locationDao()
            dao.insert(SavedLocation(id = "linked", name = "a", latitude = 1.0, longitude = 2.0, cloudId = "doc1"))
            dao.insert(SavedLocation(id = "other", name = "b", latitude = 1.0, longitude = 2.0, ownerUid = "U1"))
            dao.insert(SavedLocation(id = "local", name = "c", latitude = 1.0, longitude = 2.0))
            dao.removeAccountSpots("U2", includeLinked = false)
            assertEquals(setOf("linked", "local"), dao.getAllIncludingDeleted().map { it.id }.toSet())
        } finally {
            room.close()
        }
    }

    @Test
    fun markDeletedAndLinkStampTheOwner() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        try {
            val dao = room.locationDao()
            dao.insert(SavedLocation(id = "a", name = "a", latitude = 1.0, longitude = 2.0, ownerUid = "U1"))
            dao.insert(SavedLocation(id = "b", name = "b", latitude = 1.0, longitude = 2.0))
            dao.markDeleted("a", 5000, null)            // signed out: the owner is kept
            dao.linkCloudId("b", "doc", "U1")
            val rows = dao.getAllIncludingDeleted().associateBy { it.id }
            assertEquals("U1", rows.getValue("a").ownerUid)
            assertEquals("doc", rows.getValue("b").cloudId)
            assertEquals("U1", rows.getValue("b").ownerUid)
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
            dao.markDeleted("a", 5000, null)

            assertTrue(dao.getVisibleLocations(null).first().isEmpty())
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
