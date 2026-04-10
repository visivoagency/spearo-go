package com.spearotracker.spearogo.models

enum class TidePhase(val label: String) {
    SLACK("Slack"),
    FLOOD("Flood"),
    EBB("Ebb")
}

data class TideData(
    val currentHeight: Double,     // metres (relative)
    val isRising: Boolean,
    val phase: TidePhase,
    val nextHighTime: Long,        // epoch millis
    val nextHighHeight: Double,
    val nextLowTime: Long,         // epoch millis
    val nextLowHeight: Double,
    val fetchedAt: Long = System.currentTimeMillis()
)
