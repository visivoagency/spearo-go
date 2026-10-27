package com.spearotracker.spearogo.ui

import com.spearotracker.spearogo.ui.pages.spotCoordinates
import com.spearotracker.spearogo.ui.pages.spotSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotDetailTextTest {
    @Test fun northWest() = assertEquals("38.7223° N, 9.1393° W", spotCoordinates(38.72231, -9.13932))
    @Test fun southEast() = assertEquals("33.8688° S, 151.2093° E", spotCoordinates(-33.86882, 151.20929))
    @Test fun zeroIsNorthEast() = assertEquals("0.0000° N, 0.0000° E", spotCoordinates(0.0, 0.0))

    @Test fun sources() {
        assertEquals("Saved on this watch", spotSource("GO"))
        assertEquals("From your Spearo account", spotSource("ACCOUNT"))
        assertEquals("Saved in Vision", spotSource("VISION"))
    }
}
