package com.agon.app.data.remote

import android.content.Context
import androidx.work.*
import com.agon.app.guardianApp
import timber.log.Timber
import java.util.concurrent.TimeUnit

class FirebaseSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext.guardianApp() ?: return Result.failure()
            val repo = app.repository
            val settings = repo.getAppSettings()

            if (!settings.isRemoteMonitoringEnabled() || !settings.isShieldActive()) {
                Timber.d("FirebaseSyncWorker: remote monitoring disabled or shield inactive, skipping")
                return Result.success()
            }

            // Sync all necessary data to Firebase via repository
            repo.syncToFirebase()

            Timber.d("FirebaseSyncWorker: sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "FirebaseSyncWorker: sync failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "firebase_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            // Using PeriodicWorkRequest.Builder directly for better compatibility
            val request = PeriodicWorkRequestBuilder<FirebaseSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Timber.d("FirebaseSyncWorker: hourly sync scheduled with exponential backoff")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
