package com.spearotracker.spearogo.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The verdict must never be moved by a signal that was not measured.
 *
 * On 2026-08-31 a Galaxy Watch in Queidersbach, Rheinland-Pfalz — some 400km
 * from the sea — showed "GO, 8.3, Perfect day. No excuses." because an absent
 * marine reading was scored as a flat calm at 20C and contributed 2.7 points.
 */
class DiveScoreTest {

    @Test
    fun `all four signals present keeps the original weighting`() {
        // 9.5*.30 + 9*.30 + 7.5*.15 + 6.5*.25
        val s = DiveScore.calculate(weather = 9.5, marine = 9.0, tides = 7.5, solunar = 6.5)
        assertEquals(8.3, s.composite, 0.001)
        assertFalse(s.isPartial)
    }

    @Test
    fun `a missing signal is dropped, not scored as an average one`() {
        val s = DiveScore.calculate(weather = 9.5, marine = null, tides = 7.5, solunar = 6.5)
        // (9.5*.30 + 7.5*.15 + 6.5*.25) / 0.70
        assertEquals(8.0, s.composite, 0.001)
        assertTrue(s.isPartial)
        assertEquals(listOf("swell"), s.missingSignals)
    }

    @Test
    fun `absent marine cannot inflate the verdict`() {
        // The real failure: poor weather, no marine data. The old code scored
        // the missing sea 9/10 and dragged the composite up.
        val honest = DiveScore.calculate(weather = 3.0, marine = null, tides = 6.0, solunar = 5.0)
        val fabricated = DiveScore.calculate(weather = 3.0, marine = 9.0, tides = 6.0, solunar = 5.0)
        assertTrue(
            "a missing sea must not score better than a real bad day",
            honest.composite < fabricated.composite
        )
        assertEquals(Verdict.SKETCHY, honest.verdict)
    }

    @Test
    fun `weights still sum to one when every optional signal is missing`() {
        val s = DiveScore.calculate(weather = 8.0, marine = null, tides = null, solunar = 4.0)
        // (8*.30 + 4*.25) / 0.55
        assertEquals(6.2, s.composite, 0.05)
        assertEquals(listOf("swell", "tides"), s.missingSignals)
    }
}
