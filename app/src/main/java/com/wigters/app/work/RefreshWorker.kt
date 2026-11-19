package com.wigters.app.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wigters.app.data.ActivityRepo
import com.wigters.app.data.WeatherRepo
import com.wigters.app.widget.DashboardWidget
import com.wigters.app.widget.SystemWidget
import java.util.concurrent.TimeUnit

/** Atualiza os widgets a cada 15 minutos (o mínimo que o Android permite). */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        runCatching { WeatherRepo.refreshIfStale(ctx) }
        runCatching { ActivityRepo.refresh(ctx) }
        SystemWidget().updateAll(ctx)
        DashboardWidget().updateAll(ctx)
        return Result.success()
    }

    companion object {
        private const val NAME = "wigters-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$NAME-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RefreshWorker>().build()
            )
        }
    }
}
