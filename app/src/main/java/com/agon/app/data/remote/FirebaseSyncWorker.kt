package com.agon.app.data.remote

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit

class FirebaseSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return runBlocking {
            try {
                val app = applicationContext as com.agon.app.GuardianApp
                val settings = app.repository.getAppSettings()
                val repo = app.repository

                if (!settings.isRemoteMonitoringEnabled() || !settings.isShieldActive()) {
                    Timber.d("FirebaseSyncWorker: remote monitoring disabled or shield inactive, skipping")
                    return@runBlocking Result.success()
                }

                val firebaseManager = FirebaseManager(applicationContext, repo.blockEventDao, repo.appLimitDao)
                val initialized = firebaseManager.initialize()
                if (!initialized) {
                    Timber.w("FirebaseSyncWorker: Firebase init failed")
                    return@runBlocking Result.retry()
                }

                firebaseManager.syncDeviceInfo()
                firebaseManager.syncAppLimits()
                firebaseManager.syncBlockEvents()

                val now = System.currentTimeMillis()
                val oneWeekMs = 604800000L
                if (now % oneWeekMs < 3600000L) {
                    firebaseManager.syncWeeklyReport()
                }

                Timber.d("FirebaseSyncWorker: sync completed")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "FirebaseSyncWorker: sync failed")
                Result.retry()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "firebase_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<FirebaseSyncWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Timber.d("FirebaseSyncWorker: hourly sync scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("FirebaseSyncWorker: sync cancelled")
        }
    }
}
