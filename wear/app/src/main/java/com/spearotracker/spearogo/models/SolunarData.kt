package com.spearotracker.spearogo.models

data class SolunarData(
    val moonPhase: Double,         // 0.0 (new) - 1.0 (full cycle)
    val moonIllumination: Double,  // 0.0 - 1.0
    val moonrise: Long?,
    val moonset: Long?,
    val sunrise: Long?,
    val sunset: Long?,
    val nextMajorPeriod: Long?,    // ~2h window of peak activity
    val nextMinorPeriod: Long?,    // ~1h window of minor activity
    val activityRating: String,    // "Excellent", "Good", "Fair", "Poor"
    val fetchedAt: Long = System.currentTimeMillis()
)
