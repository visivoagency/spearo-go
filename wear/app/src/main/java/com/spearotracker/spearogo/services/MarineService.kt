package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.MarineData
import com.spearotracker.spearogo.utils.Constants
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private interface OpenMeteoMarineApi {
    @GET("v1/marine")
    suspend fun marine(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "wave_height,wave_period,wave_direction,sea_surface_temperature",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoMarineResponse
}

private data class OpenMeteoMarineResponse(
    val current: CurrentMarine?
)

private data class CurrentMarine(
    val wave_height: Double?,
    val wave_period: Double?,
    val wave_direction: Double?,
    val sea_surface_temperature: Double?
)

@Singleton
class MarineService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api: OpenMeteoMarineApi = Retrofit.Builder()
        .baseUrl(Constants.Api.MARINE_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoMarineApi::class.java)

    suspend fun fetch(latitude: Double, longitude: Double): MarineData {
        val response = api.marine(latitude, longitude)
        val current = response.current ?: throw ServiceException("Missing marine data")

        // Open-Meteo Marine answers HTTP 200 for a landlocked coordinate and
        // returns nulls, so a non-error response is NOT evidence of a sea.
        // Coalescing those nulls to 0.0 and 20.0 is what told a diver in
        // Rheinland-Pfalz the water was a flat calm at 20C, and scored it 9/10.
        val height = current.wave_height
        val temp = current.sea_surface_temperature
        if (height == null || temp == null) throw NoMarineCoverageException()

        return MarineData(
            waveHeight = height,
            wavePeriod = current.wave_period,
            waveDirection = current.wave_direction,
            seaSurfaceTemp = temp
        )
    }
}

/** The coordinate has no sea: the marine API answered, with nothing in it. */
class NoMarineCoverageException : Exception("No marine coverage for this location")
