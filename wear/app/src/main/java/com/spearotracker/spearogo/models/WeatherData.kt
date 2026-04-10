package com.spearotracker.spearogo.models

data class WeatherData(
    val windSpeed: Double,         // knots
    val windDirection: Double,     // degrees
    val windGusts: Double,         // knots
    val visibility: Double,        // km
    val cloudCover: Int,           // %
    val fetchedAt: Long = System.currentTimeMillis()
)
