package com.spearotracker.spearogo.sync

/**
 * The one-time "Save your spots to your Spearo account?" prompt (owner decision, 23 Oct): offered
 * right after the first spot is saved while signed out, until it is answered, then never again.
 * A first save while signed in uses it up. The Sign in row on the Spots screen stays for later.
 * Twin of AccountPrompt in LocationsView.swift.
 */
class AccountPromptGate(private val seen: () -> Boolean, private val markSeen: () -> Unit) {
    /** A spot was just saved. True: show the prompt now. */
    fun onSpotSaved(signedOut: Boolean): Boolean {
        if (seen()) return false
        if (!signedOut) { markSeen(); return false }
        return true
    }

    /** Sign in or Not now. Recorded only now, so a prompt cut short (app closed) comes back next save. */
    fun onAnswered() = markSeen()
}
