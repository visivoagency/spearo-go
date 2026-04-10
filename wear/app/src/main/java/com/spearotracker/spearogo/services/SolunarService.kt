package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.SolunarData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class SolunarService @Inject constructor() {

    private data class CelestialPosition(
        val longitude: Double,       // degrees
        val rightAscension: Double,  // hours
        val declination: Double      // degrees
    )

    fun calculate(latitude: Double, longitude: Double, timeMillis: Long = System.currentTimeMillis()): SolunarData {
        val jd = julianDay(timeMillis)
        val moonPos = moonPosition(jd)
        val sunPos = sunPosition(jd)

        val moonIllum = illumination(moonPos.longitude, sunPos.longitude)
        val moonPhase = moonPhaseValue(jd)

        val (moonrise, moonset) = riseSet(
            declination = moonPos.declination,
            ra = moonPos.rightAscension,
            latitude = latitude,
            longitude = longitude,
            jd = jd,
            isMoon = true
        )

        val (sunrise, sunset) = riseSet(
            declination = sunPos.declination,
            ra = sunPos.rightAscension,
            latitude = latitude,
            longitude = longitude,
            jd = jd,
            isMoon = false
        )

        // Major periods: +/-1h around moon transit (upper/lower culmination)
        val transit = moonTransit(moonPos.rightAscension, longitude, jd, timeMillis)
        val antiTransit = transit + 6 * 3600 * 1000

        val now = timeMillis

        // Next major period
        val nextMajor = listOf(transit, antiTransit)
            .filter { it > now - 3600_000 }
            .minOrNull()

        // Next minor period
        val nextMinor = listOfNotNull(moonrise, moonset)
            .filter { it > now - 1800_000 }
            .minOrNull()

        val rating = activityRating(moonPhase, moonIllum, nextMajor, now)

        return SolunarData(
            moonPhase = moonPhase,
            moonIllumination = moonIllum,
            moonrise = moonrise,
            moonset = moonset,
            sunrise = sunrise,
            sunset = sunset,
            nextMajorPeriod = nextMajor,
            nextMinorPeriod = nextMinor,
            activityRating = rating
        )
    }

    private fun julianDay(timeMillis: Long): Double {
        return timeMillis / 86400000.0 + 2440587.5
    }

    private fun moonPosition(jd: Double): CelestialPosition {
        val d = jd - 2451545.0
        val l0 = (218.316 + 13.176396 * d) % 360
        val m = (134.963 + 13.064993 * d) % 360
        val f = (93.272 + 13.229350 * d) % 360

        val lon = l0 + 6.289 * sin(Math.toRadians(m)) -
                  1.274 * sin(Math.toRadians(2 * f - m)) +
                  0.658 * sin(Math.toRadians(2 * f)) -
                  0.214 * sin(Math.toRadians(2 * m)) -
                  0.186 * sin(Math.toRadians(m - 0.0003 * (jd - 2451545.0) / 36525.0))

        val lat = 5.128 * sin(Math.toRadians(f))

        val e = 23.439 - 0.0000004 * d
        val ra = atan2(
            sin(Math.toRadians(lon)) * cos(Math.toRadians(e)) - tan(Math.toRadians(lat)) * sin(Math.toRadians(e)),
            cos(Math.toRadians(lon))
        ).let { Math.toDegrees(it) / 15.0 }

        val dec = asin(
            sin(Math.toRadians(lat)) * cos(Math.toRadians(e)) +
            cos(Math.toRadians(lat)) * sin(Math.toRadians(e)) * sin(Math.toRadians(lon))
        ).let { Math.toDegrees(it) }

        return CelestialPosition(
            longitude = lon % 360,
            rightAscension = (ra + 24) % 24,
            declination = dec
        )
    }

    private fun sunPosition(jd: Double): CelestialPosition {
        val d = jd - 2451545.0
        val g = (357.529 + 0.98560028 * d) % 360
        val q = (280.459 + 0.98564736 * d) % 360
        val lon = q + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = atan2(
            cos(Math.toRadians(e)) * sin(Math.toRadians(lon)),
            cos(Math.toRadians(lon))
        ).let { Math.toDegrees(it) / 15.0 }
        val dec = asin(sin(Math.toRadians(e)) * sin(Math.toRadians(lon))).let { Math.toDegrees(it) }
        return CelestialPosition(
            longitude = lon,
            rightAscension = (ra + 24) % 24,
            declination = dec
        )
    }

    private fun riseSet(
        declination: Double, ra: Double, latitude: Double,
        longitude: Double, jd: Double, isMoon: Boolean
    ): Pair<Long?, Long?> {
        val latR = Math.toRadians(latitude)
        val decR = Math.toRadians(declination)
        val h0 = -0.833
        val cosH = (sin(Math.toRadians(h0)) - sin(latR) * sin(decR)) / (cos(latR) * cos(decR))
        if (abs(cosH) > 1) return Pair(null, null) // circumpolar / never rises

        val hAngle = Math.toDegrees(acos(cosH))
        val noon = (ra - longitude / 15.0) % 24
        val riseH = noon - hAngle / 15.0
        val setH = noon + hAngle / 15.0

        fun toMillis(hours: Double): Long {
            val midnight = (floor(jd - 0.5) - 2440587.5) * 86400.0
            return ((midnight + hours * 3600.0) * 1000).toLong()
        }

        return Pair(toMillis(riseH), toMillis(setH))
    }

    private fun moonTransit(ra: Double, longitude: Double, jd: Double, nowMillis: Long): Long {
        val d = jd - 2451545.0
        val gmst = (280.46061837 + 360.98564736629 * d) % 360
        val lst = (gmst + longitude) % 360
        var hourAngle = ra * 15 - lst
        if (hourAngle < 0) hourAngle += 360
        val hoursUntilTransit = (360 - hourAngle) / 15.041
        return nowMillis + (hoursUntilTransit * 3600 * 1000).toLong()
    }

    private fun illumination(moonLon: Double, sunLon: Double): Double {
        val angle = abs(moonLon - sunLon) % 360
        return (1 - cos(Math.toRadians(angle))) / 2
    }

    private fun moonPhaseValue(jd: Double): Double {
        val d = jd - 2451545.0
        val raw = (d / 29.53058868) % 1
        return if (raw < 0) raw + 1 else raw
    }

    private fun activityRating(phase: Double, illum: Double, nextMajor: Long?, nowMillis: Long): String {
        val phaseScore = cos(2 * PI * phase) * 0.5 + 0.5
        var timeScore = 0.5
        if (nextMajor != null) {
            val mins = abs(nextMajor - nowMillis) / 60000.0
            timeScore = when {
                mins < 60 -> 1.0
                mins < 180 -> 0.7
                else -> 0.4
            }
        }
        val total = phaseScore * 0.5 + timeScore * 0.5
        return when {
            total >= 0.75 -> "Excellent"
            total >= 0.55 -> "Good"
            total >= 0.35 -> "Fair"
            else -> "Poor"
        }
    }
}
