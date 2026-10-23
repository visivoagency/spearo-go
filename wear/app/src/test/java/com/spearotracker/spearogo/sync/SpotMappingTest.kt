package com.spearotracker.spearogo.sync

import com.spearotracker.spearogo.models.SavedLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotMappingTest {
    @Test
    fun roundTripKeepsEveryField() {
        val row = SavedLocation(
            id = "a", name = "Lagos", latitude = 37.1, longitude = -8.67, createdAt = 10,
            isActive = true, cloudId = "doc", lastModifiedAt = 20, isDeleted = true,
            pendingSync = true, origin = "ACCOUNT"
        )
        val spot = row.toLocalSpot()
        assertEquals(SpotOrigin.ACCOUNT, spot.origin)
        assertEquals(20L, spot.lastModifiedAt)
        assertEquals(row, spot.toSavedLocation(isActive = true))
    }

    @Test
    fun legacyRowIsAGoSpotModifiedWhenCreated() {
        val spot = SavedLocation(name = "Lagos", latitude = 1.0, longitude = 2.0, createdAt = 1000).toLocalSpot()
        assertEquals(SpotOrigin.GO, spot.origin)
        assertEquals(1000L, spot.lastModifiedAt)
    }

    @Test
    fun unknownOriginReadsAsGo() {
        val spot = SavedLocation(name = "x", latitude = 1.0, longitude = 2.0, origin = "???").toLocalSpot()
        assertEquals(SpotOrigin.GO, spot.origin)
    }
}
