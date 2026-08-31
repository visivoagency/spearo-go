package com.spearotracker.spearogo.models

data class WeatherData(
    val windSpeed: Double,        // knots
    val windDirection: Double,    // degrees
    val windGusts: Double,        // knots

    // Nullable because the API does not report them everywhere. They are shown
    // as "—" rather than substituted, and a missing value is skipped by the
    // score rather than scored as a neutral one.
    val visibility: Double?,      // km
    val cloudCover: Int?,         // %

    // Today
    val airTemp: Double?,             // C, now
    val tempMax: Double?,             // C, today's high
    val tempMin: Double?,             // C, today's low
    val precipitationChance: Int?,    // %, today's max
    val weatherCode: Int?,            // WMO code

    val fetchedAt: Long = System.currentTimeMillis()
) {
    /** Plain-language description of the WMO weather code. */
    val conditionLabel: String?
        get() = when (weatherCode) {
            null -> null
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing rain"
            71, 73, 75 -> "Snow"
            77 -> "Snow grains"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm, hail"
            else -> null
        }
}
