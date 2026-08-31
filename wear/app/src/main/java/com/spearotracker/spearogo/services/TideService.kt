package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.TideData
import com.spearotracker.spearogo.models.TideEvent
import com.spearotracker.spearogo.models.TideHeight
import com.spearotracker.spearogo.models.TideType
import com.spearotracker.spearogo.utils.Constants
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private data class TidesGoRequest(val lat: Double, val lon: Double, val days: Int = 7)

private data class TidesGoResponse(
    val available: Boolean?,
    val reason: String?,
    val source: String?,
    val station: String?,
    val provenance: String?,
    val utcOffsetSeconds: Int?,
    val days: List<TidesGoDay>?
)

private data class TidesGoDay(
    val date: String?,
    val tidalRange: String?,
    val extremes: List<TidesGoExtreme>?,
    val heights: List<TidesGoHeight>?
)

private data class TidesGoExtreme(val t: Long?, val type: String?, val height: Double?)
private data class TidesGoHeight(val t: Long?, val height: Double?)

private interface TidesGoApi {
    @POST("tidesGo")
    suspend fun tides(@Body body: TidesGoRequest): TidesGoResponse
}

/**
 * Real tide predictions, from the `tidesGo` backend.
 *
 * The backend owns the whole decision — NOAA where it reaches, WorldTides
 * everywhere else — so this class contains no station lists and no tide maths.
 * That is deliberate: Spearo Go has two clients, and the previous
 * implementation held the same defect in both because the logic was written
 * twice. See spearo-go/functions/tides-go.js.
 *
 * There is no synthetic fallback. When there is nothing to show, this returns
 * null and the UI says so.
 */
@Singleton
class TideService @Inject constructor(
    private val store: TideStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api: TidesGoApi = Retrofit.Builder()
        .baseUrl(Constants.Api.FUNCTIONS_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TidesGoApi::class.java)

    /**
     * Today's tides for a location.
     *
     * Order of resort: cached day, then a network fetch covering a week, then
     * a stale cached day flagged as such, then null.
     */
    suspend fun fetch(latitude: Double, longitude: Double): TideData? {
        val today = localDateKey(System.currentTimeMillis(), 0)

        store.fresh(latitude, longitude, today)?.let { return it }

        return try {
            val response = api.tides(TidesGoRequest(latitude, longitude))
            if (response.available != true || response.days.isNullOrEmpty()) {
                // A definite "no sea here" is remembered so the watch stops
                // asking; a transient failure is not, and falls through below.
                if (response.reason == "outside_coverage") {
                    store.rememberNoCoverage(latitude, longitude)
                }
                return null
            }

            val offset = response.utcOffsetSeconds ?: 0
            val parsed = response.days.mapNotNull { day ->
                val date = day.date ?: return@mapNotNull null
                TideData(
                    date = date,
                    events = day.extremes.orEmpty().mapNotNull { e ->
                        val t = e.t ?: return@mapNotNull null
                        TideEvent(
                            timeSeconds = t,
                            type = if (e.type?.lowercase() == "high") TideType.HIGH else TideType.LOW,
                            height = e.height ?: 0.0
                        )
                    },
                    heights = day.heights.orEmpty().mapNotNull { h ->
                        val t = h.t ?: return@mapNotNull null
                        TideHeight(timeSeconds = t, height = h.height ?: 0.0)
                    },
                    stationName = response.station,
                    provenance = response.provenance,
                    utcOffsetSeconds = offset,
                    tidalRange = day.tidalRange ?: "Normal"
                )
            }

            // A week costs one credit, so every day is kept and the next six
            // are free.
            store.save(latitude, longitude, parsed)
            parsed.firstOrNull { it.date == localDateKey(System.currentTimeMillis(), offset) }
                ?: parsed.firstOrNull()
        } catch (e: Exception) {
            // Deliberately not cached as unavailable: an outage must not stick.
            store.stale(latitude, longitude, today)?.copy(isStale = true)
        }
    }

    private fun localDateKey(millis: Long, utcOffsetSeconds: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(millis + utcOffsetSeconds * 1000L))
    }
}
