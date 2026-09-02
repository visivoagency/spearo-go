package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.GeocodedPlace
import com.spearotracker.spearogo.utils.Constants
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class GeocodingResponse(val results: List<GeocodingResult>?)

internal data class GeocodingResult(
    val name: String?,
    val admin1: String?,
    val country_code: String?,
    val latitude: Double?,
    val longitude: Double?
)

private interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 8,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

/**
 * Place-name search, for adding a dive spot the diver is not standing at.
 *
 * Open-Meteo rather than Android's Geocoder, even though Geocoder works on the
 * watch — it is what already turns coordinates into the location label. The two
 * platforms' native geocoders are different services with different results, so
 * the Wear and watchOS apps would disagree about what "Lagos" means. One
 * provider answers both identically and can be pinned in a test fixture. No
 * key, and the same provider already used for weather.
 */
@Singleton
class GeocodingService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api: OpenMeteoGeocodingApi = Retrofit.Builder()
        .baseUrl(Constants.Api.GEOCODING_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoGeocodingApi::class.java)

    /** Empty is a normal answer — "no places found", not a failure. */
    suspend fun search(query: String): List<GeocodedPlace> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return parse(api.search(trimmed))
    }

    internal companion object {
        /** Split out so it can be tested against a recorded response. */
        fun parse(response: GeocodingResponse): List<GeocodedPlace> =
            response.results.orEmpty().mapNotNull { r ->
                val lat = r.latitude ?: return@mapNotNull null
                val lon = r.longitude ?: return@mapNotNull null
                val name = r.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                GeocodedPlace(
                    name = name,
                    region = r.admin1?.takeIf { it.isNotBlank() },
                    country = r.country_code?.takeIf { it.isNotBlank() },
                    latitude = lat,
                    longitude = lon
                )
            }
    }
}
