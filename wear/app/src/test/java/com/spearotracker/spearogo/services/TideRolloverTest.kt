package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.TideData
import com.spearotracker.spearogo.models.TideEvent
import com.spearotracker.spearogo.models.TideType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * The next-day rollover was first written into the network path only, so a day
 * served from cache came back without it and the tides page still read "—" for
 * the next high all evening. The fix existed; one of the two routes never
 * reached it. It is a pure function now, and both routes call this.
 */
class TideRolloverTest {

    private fun at(iso: String) = Instant.parse(iso).epochSecond

    private val week = listOf(
        TideData(
            date = "2026-09-01",
            events = listOf(
                TideEvent(at("2026-09-01T16:44:00Z"), TideType.HIGH, 3.2),
                TideEvent(at("2026-09-01T22:50:00Z"), TideType.LOW, 0.9),
            ),
            utcOffsetSeconds = 3600,
        ),
        TideData(
            date = "2026-09-02",
            events = listOf(TideEvent(at("2026-09-02T05:05:00Z"), TideType.HIGH, 3.1)),
            utcOffsetSeconds = 3600,
        ),
    )

    @Test
    fun `a day carries the next day's turns`() {
        val day = rollover(week, "2026-09-01")!!
        assertEquals(3, day.events.size)
        // Late evening, after the day's own last high.
        val evening = at("2026-09-01T20:00:00Z")
        assertEquals(at("2026-09-02T05:05:00Z"), day.nextHigh(evening)!!.timeSeconds)
    }

    @Test
    fun `the day's own identity is unchanged`() {
        val day = rollover(week, "2026-09-01")!!
        assertEquals("2026-09-01", day.date)
        assertEquals(3600, day.utcOffsetSeconds)
    }

    @Test
    fun `the last day of the week has nothing to borrow and still works`() {
        val day = rollover(week, "2026-09-02")!!
        assertEquals(1, day.events.size)
    }

    @Test
    fun `a date not in the week is absent, not an empty day`() {
        assertNull(rollover(week, "2026-09-09"))
        assertNull(rollover(emptyList(), "2026-09-01"))
    }
}
