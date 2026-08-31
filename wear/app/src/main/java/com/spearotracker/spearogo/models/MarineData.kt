package com.spearotracker.spearogo.models

data class MarineData(
    val waveHeight: Double,        // metres
    // Nullable: the API does not always report them, and a 0-second period is
    // not a calm sea, it is an absent reading. Rendered as "—" and skipped by
    // the score rather than penalised as a short period.
    val wavePeriod: Double?,       // seconds
    val waveDirection: Double?,    // degrees
    val seaSurfaceTemp: Double,    // celsius
    val fetchedAt: Long = System.currentTimeMillis()
)
