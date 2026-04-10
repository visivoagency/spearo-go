package com.spearotracker.spearogo.utils

import com.spearotracker.spearogo.models.Verdict

object PersonalityCopy {

    private val goMessages = listOf(
        "GET IN THE WATER!",
        "Fish are waiting. Go get 'em.",
        "Perfect day. No excuses.",
        "Why are you still reading this? GO!",
        "The ocean is calling.",
        "Conditions are chef's kiss.",
        "Today's the day. Suit up.",
        "Send it!"
    )

    private val maybeMessages = listOf(
        "Could be worse. Could be better.",
        "Eh, you've dove in worse.",
        "Decent. Just don't be a hero.",
        "Your call, chief.",
        "Not ideal, but fishable.",
        "The ocean shrugs at you.",
        "Proceed with mild enthusiasm."
    )

    private val sketchyMessages = listOf(
        "Think twice, dive once.",
        "Your wetsuit will earn its keep today.",
        "Spicy conditions. You sure?",
        "Only if you're feeling brave.",
        "The ocean is in a mood.",
        "Experienced divers only.",
        "Tell someone where you're going.",
        "Check your insurance first."
    )

    private val noGoMessages = listOf(
        "Nope. Netflix day.",
        "The ocean said no.",
        "Stay dry. Stay alive.",
        "Not today, friend.",
        "Hard pass.",
        "Your couch misses you anyway.",
        "Train your breath hold instead.",
        "Even the fish are hiding."
    )

    private val loadingMessages = listOf(
        "Asking the ocean...",
        "Checking the vibes...",
        "Consulting the fish...",
        "Reading the waves..."
    )

    fun message(verdict: Verdict): String = when (verdict) {
        Verdict.GO -> goMessages.random()
        Verdict.MAYBE -> maybeMessages.random()
        Verdict.SKETCHY -> sketchyMessages.random()
        Verdict.NO_GO -> noGoMessages.random()
    }

    fun loading(): String = loadingMessages.random()
}
