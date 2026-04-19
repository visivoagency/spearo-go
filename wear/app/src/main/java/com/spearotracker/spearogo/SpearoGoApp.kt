package com.spearotracker.spearogo

import android.app.Application
import com.spearotracker.spearogo.services.RefreshWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpearoGoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
