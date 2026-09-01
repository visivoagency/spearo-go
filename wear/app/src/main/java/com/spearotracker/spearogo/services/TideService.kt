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

/**
 * Why there are no tides, when there are none. A coordinate with no sea is a
 * settled fact; a failed lookup is not, and the two must not read alike.
 */
sealed interface TideLookup {
    data class Found(val data: TideData) : TideLookup
    data object NoCoverage : TideLookup
    data object Unavailable : TideLookup
}

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
    suspend fun fetch(latitude: Double, longitude: Double): TideLookup {
        val today = localDateKey(System.currentTimeMillis(), 0)

        store.fresh(latitude, longitude, today)?.let { return TideLookup.Found(it) }
        if (store.knownWithoutCoverage(latitude, longitude)) return TideLookup.NoCoverage

        return try {
            val response = api.tides(TidesGoRequest(latitude, longitude))
            if (response.available != true || response.days.isNullOrEmpty()) {
                // A definite "no sea here" is remembered so the watch stops
                // asking; a transient failure is not, and falls through below.
                if (response.reason == "outside_coverage") {
                    store.rememberNoCoverage(latitude, longitude)
                    return TideLookup.NoCoverage
                }
                return TideLookup.Unavailable
            }

            val offset = response.utcOffsetSeconds ?: 0
            val parsed = response.days.mapNotNull { day ->
                val date = day.date ?: return@mapNotNull null
                TideData(
                    date = date,
                    events = day.extremes.orEmpty().mapNotNull { e ->
                        // 0m is a real chart-datum height, so a missing one
                        // must be dropped rather than floored into a plausible
                        // reading.
                        val t = e.t ?: return@mapNotNull null
                        val height = e.height ?: return@mapNotNull null
                        TideEvent(
                            timeSeconds = t,
                            type = if (e.type?.lowercase() == "high") TideType.HIGH else TideType.LOW,
                            height = height
                        )
                    },
                    heights = day.heights.orEmpty().mapNotNull { h ->
                        val t = h.t ?: return@mapNotNull null
                        val height = h.height ?: return@mapNotNull null
                        TideHeight(timeSeconds = t, height = height)
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
            val todayKey = localDateKey(System.currentTimeMillis(), offset)
            val index = parsed.indexOfFirst { it.date == todayKey }.takeIf { it >= 0 } ?: 0
            val today = parsed.getOrNull(index)

            if (today == null) {
                TideLookup.Unavailable
            } else {
                // Carry the following day's turns so "next high" still has an
                // answer late in the evening. Without this the page reads "—"
                // from the day's last high until midnight, which is precisely
                // when someone is planning tomorrow's dive.
                val withTomorrow = today.copy(
                    events = today.events + parsed.getOrNull(index + 1)?.events.orEmpty()
                )
                TideLookup.Found(withTomorrow)
            }
        } catch (e: Exception) {
            // Deliberately not cached as unavailable: an outage must not stick.
            val stale = store.stale(latitude, longitude, today)
            if (stale == null) TideLookup.Unavailable else TideLookup.Found(stale.copy(isStale = true))
        }
    }

    private fun localDateKey(millis: Long, utcOffsetSeconds: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(millis + utcOffsetSeconds * 1000L))
    }
}
