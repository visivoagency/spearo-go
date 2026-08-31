package com.spearotracker.spearogo.services

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/**
 * Ground truth computed independently (Meeus, full periodic terms) for
 * Queidersbach, Rheinland-Pfalz on 2026-08-31, and confirmed against a real
 * Galaxy Watch Ultra that displayed a major period of 12:39 CEST — which
 * matched no lunar transit at all.
 *
 * Lunar transits that day at 49.3486 N, 7.6486 E:
 *   upper culmination  04:21 CEST  =  02:21 UTC (1 Sep)
 *   lower culmination  16:46 CEST  =  14:46 UTC (1 Sep)
 * The two are 12h25m apart, as any pair of culminations must be.
 *
 * Tolerance is 30 minutes: the service uses a truncated lunar series, which is
 * accurate to roughly 18 minutes here and entirely adequate for placing a
 * two-hour feeding window. It is not adequate to be 3 hours out.
 */
class SolunarServiceTest {

    private val service = SolunarService()
    private val lat = 49.3486
    private val lon = 7.6486
    private val toleranceMs = 30 * 60 * 1000L

    private fun assertNear(expectedIso: String, actual: Long?, label: String) {
        requireNotNull(actual) { "$label was null" }
        val expected = Instant.parse(expectedIso).toEpochMilli()
        val driftMin = abs(actual - expected) / 60000.0
        assertTrue(
            "$label was ${Instant.ofEpochMilli(actual)}, expected near $expectedIso " +
                "(off by ${"%.0f".format(driftMin)} min)",
            abs(actual - expected) <= toleranceMs
        )
    }

    @Test
    fun `next major period is the upper culmination, not eight hours away`() {
        val now = Instant.parse("2026-08-31T18:20:00Z").toEpochMilli()
        val data = service.calculate(lat, lon, now)
        assertNear("2026-09-01T02:21:00Z", data.nextMajorPeriod, "nextMajorPeriod")
    }

    @Test
    fun `the major period after a culmination is the opposite culmination`() {
        // Clear of the upper culmination's window. The service deliberately
        // keeps a major period "next" for an hour after it starts, since the
        // window itself is about two hours long — so this has to be more than
        // an hour past 02:21Z to be asking what the test means to ask.
        // The answer must then be the LOWER culmination 12h25m later, not the
        // 6h the code used to assume.
        val now = Instant.parse("2026-09-01T04:00:00Z").toEpochMilli()
        val data = service.calculate(lat, lon, now)
        assertNear("2026-09-01T14:46:00Z", data.nextMajorPeriod, "nextMajorPeriod")
    }

    @Test
    fun `moon illumination matches an independent ephemeris`() {
        val now = Instant.parse("2026-08-31T18:20:00Z").toEpochMilli()
        val data = service.calculate(lat, lon, now)
        assertTrue(
            "illumination was ${data.moonIllumination}, expected ~0.86",
            abs(data.moonIllumination - 0.86) < 0.03
        )
    }
}
