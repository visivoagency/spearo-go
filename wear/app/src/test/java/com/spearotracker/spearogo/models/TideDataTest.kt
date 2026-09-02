package com.spearotracker.spearogo.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The real Lagos, Portugal tide day, taken from the live tidesGo backend on
 * 2026-08-31 — the customer's own spot.
 *
 *   HIGH 04:50  3.28m     LOW 10:49  0.69m
 *   HIGH 17:07  3.40m     LOW 23:13  0.72m   (local, UTC+1)
 *
 * The app previously showed a LOW at 04:49 and a HIGH at 11:00 here: the timing
 * was nearly right and the tides were INVERTED, sending divers in at exactly
 * the wrong end of the tide. These tests pin the derivation that replaces it.
 */
class TideDataTest {

    private fun at(iso: String) = Instant.parse(iso).epochSecond

    private val lagos = TideData(
        date = "2026-08-31",
        events = listOf(
            TideEvent(at("2026-08-31T03:50:00Z"), TideType.HIGH, 3.28),
            TideEvent(at("2026-08-31T09:49:00Z"), TideType.LOW, 0.69),
            TideEvent(at("2026-08-31T16:07:00Z"), TideType.HIGH, 3.40),
            TideEvent(at("2026-08-31T22:13:00Z"), TideType.LOW, 0.72),
        ),
        heights = listOf(
            TideHeight(at("2026-08-31T12:00:00Z"), 1.20),
            TideHeight(at("2026-08-31T13:00:00Z"), 2.00),
        ),
        stationName = "Lagos",
        provenance = "gauge",
        utcOffsetSeconds = 3600,
        tidalRange = "Spring",
    )

    @Test
    fun `the next high and low are the ones that actually come next`() {
        val now = at("2026-08-31T06:00:00Z") // 07:00 local, after the first high
        assertEquals(at("2026-08-31T16:07:00Z"), lagos.nextHigh(now)!!.timeSeconds)
        assertEquals(at("2026-08-31T09:49:00Z"), lagos.nextLow(now)!!.timeSeconds)
    }

    @Test
    fun `falling towards a low is not reported as rising`() {
        // The old model had Lagos inverted; this is the case that catches it.
        val now = at("2026-08-31T06:00:00Z")
        assertEquals(TideType.LOW, lagos.nextEvent(now)!!.type)
        assertTrue("water is falling towards the 09:49 low", !lagos.isRising(now))
    }

    @Test
    fun `rising towards a high is reported as rising`() {
        val now = at("2026-08-31T12:00:00Z")
        assertTrue(lagos.isRising(now))
        assertEquals(TidePhase.FLOOD, lagos.phase(now))
    }

    @Test
    fun `the half hour either side of a turn is slack`() {
        assertEquals(TidePhase.SLACK, lagos.phase(at("2026-08-31T09:30:00Z")))
        assertEquals(TidePhase.SLACK, lagos.phase(at("2026-08-31T10:15:00Z")))
        assertEquals(TidePhase.EBB, lagos.phase(at("2026-08-31T08:00:00Z")))
    }

    @Test
    fun `current height interpolates between the hourly readings`() {
        val mid = lagos.currentHeight(at("2026-08-31T12:30:00Z"))!!
        assertEquals(1.60, mid, 0.001)
    }

    @Test
    fun `an unbracketed moment reports no height rather than guessing one`() {
        assertNull(lagos.currentHeight(at("2026-08-31T20:00:00Z")))
    }

    @Test
    fun `after the last turn of the day there is no next tide, not a wrong one`() {
        val now = at("2026-08-31T23:30:00Z")
        assertNull(lagos.nextHigh(now))
        assertNull(lagos.nextLow(now))
    }

    @Test
    fun `a gauge reading is not flagged as an estimate`() {
        assertTrue(!lagos.isModelEstimate)
        assertTrue(lagos.copy(provenance = "model").isModelEstimate)
    }

    @Test
    fun `times render in the station's local zone, not the reader's`() {
        // The Lagos low at 23:50 WEST was showing as 00:50 on a watch set to
        // CEST — the next day, and an hour out. A diver comparing the app to a
        // printed Portuguese tide table must see the printed number.
        val low = TideEvent(at("2026-09-01T22:50:00Z"), TideType.LOW, 0.9)
        val lagos = TideData(date = "2026-09-01", events = listOf(low), utcOffsetSeconds = 3600)

        val rendered = java.time.Instant.ofEpochMilli(lagos.stationLocalMillis(low))
            .atZone(java.time.ZoneOffset.UTC)
        assertEquals(23, rendered.hour)
        assertEquals(50, rendered.minute)
    }

    @Test
    fun `late in the evening the next high comes from tomorrow`() {
        // After the day's last high there is no further high today. Reading
        // only today's events showed "—" from mid-evening until midnight,
        // which is exactly when tomorrow's dive gets planned.
        val today = listOf(
            TideEvent(at("2026-09-01T16:44:00Z"), TideType.HIGH, 3.2),
            TideEvent(at("2026-09-01T22:50:00Z"), TideType.LOW, 0.9),
        )
        val tomorrow = listOf(
            TideEvent(at("2026-09-02T05:05:00Z"), TideType.HIGH, 3.1),
        )
        val withRollover = TideData(
            date = "2026-09-01",
            events = today + tomorrow,
            utcOffsetSeconds = 3600,
        )
        val evening = at("2026-09-01T20:00:00Z")
        assertEquals(at("2026-09-02T05:05:00Z"), withRollover.nextHigh(evening)!!.timeSeconds)
        assertEquals(at("2026-09-01T22:50:00Z"), withRollover.nextLow(evening)!!.timeSeconds)
    }
}
