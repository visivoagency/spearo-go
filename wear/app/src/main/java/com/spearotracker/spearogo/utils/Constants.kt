package com.spearotracker.spearogo.utils

object Constants {
    object Weights {
        const val WEATHER = 0.30
        const val MARINE = 0.30
        const val TIDES = 0.15
        const val SOLUNAR = 0.25
    }

    object Api {
        const val WEATHER_BASE = "https://api.open-meteo.com/"
        const val MARINE_BASE = "https://marine-api.open-meteo.com/"
    }

    object App {
        const val NAME = "Spearo Go"
        const val VERSION = "1.0.0"
        const val PRICE = "$2.99"
    }
}
