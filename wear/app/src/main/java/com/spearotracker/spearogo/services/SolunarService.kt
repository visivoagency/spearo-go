package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.SolunarData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import java.util.Calendar

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

        val (moonrise, moonset) = riseSet(latitude, longitude, timeMillis, isMoon = true)
        val (sunrise, sunset) = riseSet(latitude, longitude, timeMillis, isMoon = false)

        // Major periods: ~2h windows around the Moon's culminations.
        val now = timeMillis

        // Culminations alternate every 12h25m: the Moon crosses the meridian
        // (upper) and the anti-meridian (lower). Both are solved directly
        // rather than derived by adding a fixed offset to the other, because
        // the Moon's motion is not uniform.
        val culminations = listOf(
            nextCulmination(longitude, now, targetHourAngleDeg = 0.0),
            nextCulmination(longitude, now, targetHourAngleDeg = 180.0)
        )

        // Next major period
        val nextMajor = culminations
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

    /**
     * Altitude of a body above the horizon, in degrees.
     */
    private fun altitude(latitude: Double, longitude: Double, millis: Long, isMoon: Boolean): Double {
        val j = julianDay(millis)
        val pos = if (isMoon) moonPosition(j) else sunPosition(j)
        val d = j - 2451545.0
        val gmst = (280.46061837 + 360.98564736629 * d) % 360
        val hourAngle = (gmst + longitude - pos.rightAscension * 15) % 360
        val latR = Math.toRadians(latitude)
        val decR = Math.toRadians(pos.declination)
        return Math.toDegrees(
            asin(sin(latR) * sin(decR) + cos(latR) * cos(decR) * cos(Math.toRadians(hourAngle)))
        )
    }

    /**
     * Rise and set for the local day containing [timeMillis], found by scanning
     * the body's altitude for crossings of the horizon.
     *
     * The previous implementation solved for an hour angle and then placed it
     * with `ra - longitude / 15`, which never referenced sidereal time and so
     * was not anchored to the date at all. On 2026-08-31 it put sunrise at
     * 17:24 and sunset at 06:54, inverted and thirteen hours out. A scan costs
     * a few hundred cheap evaluations and cannot get the day wrong.
     *
     * Returns nulls when the body does not cross the horizon that day, which is
     * the honest answer inside a polar summer or winter — and for the Moon,
     * simply on days when it does not rise.
     */
    private fun riseSet(
        latitude: Double,
        longitude: Double,
        timeMillis: Long,
        isMoon: Boolean
    ): Pair<Long?, Long?> {
        // Standard refraction for the Sun's upper limb; for the Moon, parallax
        // very nearly cancels semidiameter and refraction.
        val horizon = if (isMoon) 0.125 else -0.833

        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis

        val step = 5 * 60 * 1000L
        val dayEnd = dayStart + 24 * 3600 * 1000L

        var rise: Long? = null
        var set: Long? = null
        var previousAlt = altitude(latitude, longitude, dayStart, isMoon)
        var t = dayStart + step

        while (t <= dayEnd) {
            val alt = altitude(latitude, longitude, t, isMoon)
            // Linear interpolation across the step gets within a few seconds.
            if (previousAlt < horizon && alt >= horizon && rise == null) {
                rise = t - step + ((horizon - previousAlt) / (alt - previousAlt) * step).toLong()
            }
            if (previousAlt > horizon && alt <= horizon && set == null) {
                set = t - step + ((previousAlt - horizon) / (previousAlt - alt) * step).toLong()
            }
            previousAlt = alt
            t += step
        }

        return Pair(rise, set)
    }

    /**
     * Time of the Moon's next crossing of a given hour angle — 0 for upper
     * culmination, 180 for lower. These are the solunar "major" periods.
     *
     * Solved iteratively. A single pass using the Moon's position *now* is what
     * this code used to do, and it is wrong by up to an hour for a culmination
     * a day away, because the Moon moves about half a degree an hour against
     * the stars. Re-evaluating at the estimate converges in a couple of passes.
     */
    private fun nextCulmination(longitude: Double, fromMillis: Long, targetHourAngleDeg: Double): Long {
        var t = fromMillis
        repeat(4) {
            val j = julianDay(t)
            val ra = moonPosition(j).rightAscension
            val d = j - 2451545.0
            val gmst = (280.46061837 + 360.98564736629 * d) % 360
            val lst = (gmst + longitude) % 360
            // How far past the target the Moon already is, in degrees,
            // normalised to (-180, 180].
            var past = lst - ra * 15 - targetHourAngleDeg
            past = ((past + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            t += ((-past / SIDEREAL_DEGREES_PER_HOUR) * 3600 * 1000).toLong()
        }
        // Converges on the NEAREST crossing, which may be just behind us.
        while (t < fromMillis - 3600_000) t += LUNAR_DAY_MS
        return t
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

// Rate at which the sky turns relative to the Moon, degrees per hour: the Moon
// moves eastward against the stars, so it returns to the meridian every ~24h50m
// rather than every sidereal day.
private const val SIDEREAL_DEGREES_PER_HOUR = 15.041

// A lunar day: the interval between successive upper culminations, 24h50m28s.
private const val LUNAR_DAY_MS = 89_428_320L
