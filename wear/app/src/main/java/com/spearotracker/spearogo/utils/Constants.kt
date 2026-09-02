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

        // Cloud Functions for Spearo Go, deployed under the `spearogo` codebase
        // of the shared spearo-tracker project. See spearo-go/functions.
        const val FUNCTIONS_BASE = "https://us-central1-spearo-tracker.cloudfunctions.net/"

        // Place-name search, for adding a spot you are not standing at.
        // Same provider as the weather API, no key required.
        const val GEOCODING_BASE = "https://geocoding-api.open-meteo.com/"
    }

    object App {
        const val NAME = "Spearo Go"
        const val VERSION = "1.0.0"
        const val PRICE = "$2.99"
    }
}
