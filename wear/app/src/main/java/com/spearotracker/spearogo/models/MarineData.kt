package com.spearotracker.spearogo.models

data class MarineData(
    val waveHeight: Double,        // metres
    val wavePeriod: Double,        // seconds
    val waveDirection: Double,     // degrees
    val seaSurfaceTemp: Double,    // celsius
    val fetchedAt: Long = System.currentTimeMillis()
)
