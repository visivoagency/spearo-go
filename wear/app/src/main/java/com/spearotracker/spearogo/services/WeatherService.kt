package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.WeatherData
import com.spearotracker.spearogo.utils.Constants
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private interface OpenMeteoWeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility,temperature_2m,weather_code",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code",
        @Query("wind_speed_unit") windSpeedUnit: String = "ms",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1
    ): OpenMeteoWeatherResponse
}

private data class OpenMeteoWeatherResponse(
    val current: CurrentWeather?,
    val daily: DailyWeather?
)

private data class CurrentWeather(
    val wind_speed_10m: Double?,
    val wind_direction_10m: Double?,
    val wind_gusts_10m: Double?,
    val cloud_cover: Int?,
    val visibility: Double?,
    val temperature_2m: Double?,
    val weather_code: Int?
)

private data class DailyWeather(
    val temperature_2m_max: List<Double?>?,
    val temperature_2m_min: List<Double?>?,
    val precipitation_probability_max: List<Int?>?,
    val weather_code: List<Int?>?
)

@Singleton
class WeatherService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api: OpenMeteoWeatherApi = Retrofit.Builder()
        .baseUrl(Constants.Api.WEATHER_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoWeatherApi::class.java)

    suspend fun fetch(latitude: Double, longitude: Double): WeatherData {
        val response = api.forecast(latitude, longitude)
        val current = response.current ?: throw ServiceException("Missing weather data")

        // Wind is the one reading this app cannot do without, and a missing
        // wind speed must not become a flat calm - a 0 kn default reads as
        // ideal conditions. Everything else is carried through as nullable and
        // rendered as "—" rather than filled in.
        val windMS = current.wind_speed_10m
        val windDir = current.wind_direction_10m
        if (windMS == null || windDir == null) throw ServiceException("Missing wind data")

        // Convert m/s to knots
        val windKnots = windMS * 1.94384
        val gustsKnots = current.wind_gusts_10m?.times(1.94384)

        val daily = response.daily

        return WeatherData(
            windSpeed = windKnots,
            windDirection = windDir,
            windGusts = gustsKnots ?: windKnots,
            visibility = current.visibility?.div(1000.0),
            cloudCover = current.cloud_cover,
            airTemp = current.temperature_2m,
            tempMax = daily?.temperature_2m_max?.firstOrNull(),
            tempMin = daily?.temperature_2m_min?.firstOrNull(),
            precipitationChance = daily?.precipitation_probability_max?.firstOrNull(),
            weatherCode = daily?.weather_code?.firstOrNull() ?: current.weather_code
        )
    }
}
