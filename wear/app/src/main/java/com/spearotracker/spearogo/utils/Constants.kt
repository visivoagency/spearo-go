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

    /**
     * Optional sign-in and spot sync, against the shared `spearo-tracker` project over REST
     * (no Firebase SDK). The API key is a Firebase client identifier, not a secret: this is
     * the project's Android key (from Apnea's google-services.json). The OAuth client
     * secret is a secret and comes only from local.properties via BuildConfig.
     */
    object Firebase {
        const val API_KEY = "AIzaSyCCzSREllNDQE3u76gB4bFqfiiqAESztHs"
        const val PROJECT_ID = "spearo-tracker"
        // Same web client as Apnea, so one Google account gives one uid across apps.
        const val WEB_CLIENT_ID = "420670186721-fgqn7l067ppl3mk9mm2cq1lt5ke5i0kk.apps.googleusercontent.com"
        const val IDENTITY_BASE = "https://identitytoolkit.googleapis.com"
        const val SECURE_TOKEN_BASE = "https://securetoken.googleapis.com"
        const val FIRESTORE_BASE =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"
    }

    object App {
        const val NAME = "Spearo Go"
        const val VERSION = "2.2.0"   // Shown on the Info page; keep with build.gradle.kts versionName.
        const val PRICE = "$0.99"
    }
}
