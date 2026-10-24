package com.spearotracker.spearogo.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPromptGateTest {
    private var seen = false
    private val gate = AccountPromptGate(seen = { seen }, markSeen = { seen = true })

    @Test
    fun firstSpotSavedSignedOutShowsThePromptUntilAnswered() {
        assertTrue(gate.onSpotSaved(signedOut = true))
        assertTrue(gate.onSpotSaved(signedOut = true))      // closed without an answer: offered again
        gate.onAnswered()
        assertFalse(gate.onSpotSaved(signedOut = true))
    }

    @Test
    fun firstSpotSavedSignedInUsesUpThePrompt() {
        assertFalse(gate.onSpotSaved(signedOut = false))
        assertFalse(gate.onSpotSaved(signedOut = true))   // signed out later: still never shown
    }
}
