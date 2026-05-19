package com.agon.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.agon.app.data.GuardianRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class GuardianApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }

        appScope.launch {
            val repo = GuardianRepository(this@GuardianApp)
            val state = repo.guardianStateFlow.first()
            if (state.onboardingCompleted && !state.pinCode.isNullOrEmpty()) {
                repo.updateAppUnlocked(false)
            }
        }
    }
}