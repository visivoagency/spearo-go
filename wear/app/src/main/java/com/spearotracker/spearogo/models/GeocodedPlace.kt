package com.spearotracker.spearogo.models

/**
 * A place returned by a name search.
 *
 * [region] and [country] are not decoration. Searching "Lagos" returns four
 * places — Nigeria, France, Spain and Portugal — and the Portuguese one an
 * Algarve diver wants is fourth. A list of four rows reading "Lagos" would be
 * worse than no search at all.
 */
data class GeocodedPlace(
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double
) {
    /** "Lagos · Faro District, PT" — enough to tell four Lagoses apart. */
    val label: String
        get() = listOfNotNull(region, country).joinToString(", ").ifEmpty { name }

    /** What gets stored as the spot's name. */
    val savedName: String
        get() = listOfNotNull(name, region).joinToString(", ")
}
