package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.WeatherData
import com.spearotracker.spearogo.utils.Constants
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

private interface OpenMeteoWeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility",
        @Query("wind_speed_unit") windSpeedUnit: String = "ms",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1
    ): OpenMeteoWeatherResponse
}

private data class OpenMeteoWeatherResponse(
    val current: CurrentWeather?
)

private data class CurrentWeather(
    val wind_speed_10m: Double?,
    val wind_direction_10m: Double?,
    val wind_gusts_10m: Double?,
    val cloud_cover: Int?,
    val visibility: Double?
)

@Singleton
class WeatherService @Inject constructor() {

    private val api: OpenMeteoWeatherApi = Retrofit.Builder()
        .baseUrl(Constants.Api.WEATHER_BASE)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoWeatherApi::class.java)

    suspend fun fetch(latitude: Double, longitude: Double): WeatherData {
        val response = api.forecast(latitude, longitude)
        val current = response.current ?: throw ServiceException("Missing weather data")

        // Convert m/s to knots
        val windKnots = (current.wind_speed_10m ?: 0.0) * 1.94384
        val gustsKnots = (current.wind_gusts_10m ?: 0.0) * 1.94384

        return WeatherData(
            windSpeed = windKnots,
            windDirection = current.wind_direction_10m ?: 0.0,
            windGusts = gustsKnots,
            visibility = (current.visibility ?: 10000.0) / 1000.0,
            cloudCover = current.cloud_cover ?: 0
        )
    }
}
