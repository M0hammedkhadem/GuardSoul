package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.agon.app.data.GuardianRepository
import com.agon.app.data.GuardianState
import com.agon.app.services.detector.AppBlocker
import com.agon.app.services.detector.ShortVideoBlocker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class GuardianAccessibilityService : AccessibilityService() {
    private val job = SupervisorJob()
    val scope = CoroutineScope(Dispatchers.IO + job)
    lateinit var repository: GuardianRepository
        private set
    var currentState: GuardianState = GuardianState()
        private set

    var debounceJob: Job? = null
    private val TAG = "GuardianService"

    private lateinit var shortVideoBlocker: ShortVideoBlocker
    private lateinit var appBlocker: AppBlocker

    override fun onServiceConnected() {
        super.onServiceConnected()

        repository = GuardianRepository(applicationContext)
        shortVideoBlocker = ShortVideoBlocker(this, repository, { currentState }, { currentState = it })
        appBlocker = AppBlocker(this, repository, { currentState })

        shortVideoBlocker.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !currentState.isShieldActive) return
        val packageName = event.packageName?.toString() ?: return
        if (currentState.whitelistApps.contains(packageName)) return

        val blockReason = appBlocker.getFullBlockReason(packageName)
        if (blockReason != null) {
            appBlocker.executeFullBlock(packageName, blockReason)
            return
        }

        Timber.tag(TAG).d("Event: type=${event.eventType} pkg=$packageName")

        if (packageName == "com.google.android.youtube" && currentState.youtubeMode == "shorts") {
            shortVideoBlocker.handleYoutubeShorts(event, packageName)
        }

        if (packageName in ShortVideoBlocker.FacebookPackages && currentState.facebookMode == "reels") {
            shortVideoBlocker.handleFacebookReelsEvent(event, packageName)
        }

        if (packageName in ShortVideoBlocker.InstagramPackages && !currentState.instagramBlocked) {
            shortVideoBlocker.handleInstagramReelsEvent(event, packageName)
        }

        val fallbackReason = appBlocker.getFullBlockReason(packageName)
        if (fallbackReason != null) {
            appBlocker.executeFullBlock(packageName, fallbackReason)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
