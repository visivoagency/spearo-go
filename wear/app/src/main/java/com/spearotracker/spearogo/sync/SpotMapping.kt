package com.spearotracker.spearogo.sync

import com.spearotracker.spearogo.models.SavedLocation

/** SavedLocation.origin holds the SpotOrigin enum name. A value we do not know reads as GO, never as a remote origin. */
private fun originOf(name: String): SpotOrigin =
    SpotOrigin.values().firstOrNull { it.name == name } ?: SpotOrigin.GO

fun SavedLocation.toLocalSpot(): LocalSpot = LocalSpot(
    id = id, cloudId = cloudId, name = name,
    latitude = latitude, longitude = longitude,
    createdAt = createdAt, lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted, pendingSync = pendingSync, origin = originOf(origin)
)

/**
 * isActive is local-only state, and ownerUid is the account the row belongs to (the merge does
 * not carry it), so the caller says what each should be.
 */
fun LocalSpot.toSavedLocation(isActive: Boolean, ownerUid: String?): SavedLocation = SavedLocation(
    id = id, name = name, latitude = latitude, longitude = longitude,
    createdAt = createdAt, isActive = isActive,
    cloudId = cloudId, lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted, pendingSync = pendingSync, origin = origin.name,
    ownerUid = ownerUid
)
