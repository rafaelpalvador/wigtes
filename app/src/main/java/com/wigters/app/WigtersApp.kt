package com.wigters.app

import android.app.Application
import com.wigters.app.work.RefreshWorker

class WigtersApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
