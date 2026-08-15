package com.agon.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.agon.app.data.repository.ProtectionRepository
import com.agon.app.engine.AppPolicy
import com.agon.app.engine.BlockCause
import com.agon.app.engine.BlockDecision
import com.agon.app.engine.BlockOverlay
import com.agon.app.engine.DetectionEngine
import com.agon.app.engine.NsfwClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * The sensory layer of the protection brain.
 *
 * Feeds accessibility events, node trees and throttled screenshots into
 * [DetectionEngine]; executes its [BlockDecision]s (overlay + back/home).
 *
 * Settings come exclusively from [ProtectionRepository.engineSettingsFlow]
 * (single source of truth — no duplicated JSON parsing here).
 */
@AndroidEntryPoint
class ProtectionAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var protectionRepo: ProtectionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var nsfw: NsfwClassifier
    private lateinit var engine: DetectionEngine
    private lateinit var overlay: BlockOverlay
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile
    private var screenshotBusy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        nsfw = NsfwClassifier(this)
        engine = DetectionEngine(nsfw)
        overlay = BlockOverlay(this)

        // Live settings: any toggle in the UI reaches the brain instantly.
        // Single source of truth — the repository builds EngineSettings with
        // exactly the same defaults the old inline block used.
        scope.launch {
            protectionRepo.engineSettingsFlow.collect { engine.settings = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return // never police ourselves
        val now = System.currentTimeMillis()

        val isLaunchEvent = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (isLaunchEvent) {
            engine.onPackageChanged(pkg)
        }

        // Tamper protection: block uninstall / force-stop / clear-data screens.
        engine.checkUninstallGuard(rootInActiveWindow, pkg, now)?.let { execute(it); return }

        // Whitelisted apps are exempt from EVERY kind of blocking.
        if (engine.isWhitelistedApp(pkg)) return

        // Full app block — checked on EVERY event (cheap map lookup). Real
        // launch events bypass the long suppress window so an instant
        // relaunch of the same blocked app is ALWAYS re-blocked.
        engine.checkFullBlock(pkg, now, isLaunchEvent)?.let { execute(it); return }

        // Node-tree based checks (cheap) — run on content/state changes.
        val root = rootInActiveWindow
        val dm = resources.displayMetrics
        engine.checkGenericShorts(root, pkg, dm.widthPixels, dm.heightPixels, now)
            ?.let { execute(it); return }
        engine.checkBrowser(root, pkg, now)?.let { execute(it); return }

        // Screen-wide keyword guard: any blacklisted word visible anywhere
        // on screen (not just search bars) -> block + BACK.
        engine.checkScreenKeywords(root, pkg, now)?.let { execute(it); return }

        // Screenshot-based checks (expensive) — throttled inside the engine.
        val needs = engine.screenshotNeeds(pkg, now)
        if (needs.any && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !screenshotBusy) {
            captureAndAnalyze(pkg, needs, now)
        } else if (needs.tabBar) {
            // Below API 30: mechanism #2 (action rail) still protects alone.
            engine.checkFacebookReels(
                root = root, screenshot = null,
                statusBarPx = statusBarHeight(), densityDpi = resources.displayMetrics.densityDpi,
                screenW = resources.displayMetrics.widthPixels,
                screenH = resources.displayMetrics.heightPixels,
                now = now,
            )?.let { execute(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureAndAnalyze(pkg: String, needs: DetectionEngine.ScreenshotNeeds, now: Long) {
        screenshotBusy = true
        val executor = Executor { r -> scope.launch { r.run() } }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    scope.launch {
                        try {
                            val hw = Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer, result.colorSpace,
                            )
                            result.hardwareBuffer.close()
                            val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hw?.recycle()
                            if (bmp != null) {
                                analyze(pkg, needs, bmp, now)
                                bmp.recycle()
                            }
                        } finally {
                            screenshotBusy = false
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    screenshotBusy = false
                }
            },
        )
    }

    private fun analyze(pkg: String, needs: DetectionEngine.ScreenshotNeeds, bmp: Bitmap, now: Long) {
        if (needs.tabBar && AppPolicy.isFacebook(pkg)) {
            engine.checkFacebookReels(
                root = rootInActiveWindow,
                screenshot = bmp,
                statusBarPx = statusBarHeight(),
                densityDpi = resources.displayMetrics.densityDpi,
                screenW = bmp.width,
                screenH = bmp.height,
                now = now,
            )?.let { execute(it); return }
        }
        if (needs.nsfw) {
            // Pass the full-resolution frame; the classifier applies the
            // canonical open_nsfw preprocessing (256 resize + 224 center crop)
            // itself — avoids a double-resize that degraded accuracy.
            engine.checkNsfw(bmp, now)?.let { execute(it) }
        }
    }

    private fun execute(decision: BlockDecision) {
        overlay.show(
            title = decision.title,
            message = decision.message,
            autoHideMs = decision.overlayMs,
            buttonLabel = decision.buttonLabel,
            buttonGoesHome = decision.goHome,
            opaque = decision.opaqueOverlay,
            secondaryLabel = if (decision.allowContinue) "المواصلة رغم التحذير" else null,
            onSecondary = if (decision.allowContinue && decision.targetPackage != null) {
                { engine.snoozeKeywords(decision.targetPackage, System.currentTimeMillis()) }
            } else null,
        )
        if (decision.autoAction) {
            if (decision.goHome) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                // Fierce app blocking: a single HOME can lose the race against
                // the app's own animations/dialogs. The watchdog keeps hammering
                // HOME until the blocked app is really off the screen.
                if (decision.cause == BlockCause.APP && decision.targetPackage != null) {
                    startAppBlockWatchdog(decision.targetPackage)
                }
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                // On a repeat attempt, one BACK may land on the same content
                // (e.g. browser history) — push a second BACK to break the loop.
                if (decision.repeatCount > 0) {
                    mainHandler.postDelayed(
                        { performGlobalAction(GLOBAL_ACTION_BACK) },
                        350L,
                    )
                }
            }
        }
        scope.launch { protectionRepo.incrementBlocksCount() }
    }

    // ---------- Fierce app-block watchdog ----------

    @Volatile
    private var watchdogPackage: String? = null

    /**
     * Keeps kicking to HOME every 500ms (up to 10 ticks ≈ 5s) while the
     * blocked app is still in the foreground. Stops immediately once another
     * package takes over. Re-arming for the same package just extends it.
     */
    private fun startAppBlockWatchdog(pkg: String) {
        if (watchdogPackage == pkg) return // already running for this app
        watchdogPackage = pkg
        var ticks = 0
        lateinit var tick: Runnable
        tick = Runnable {
            if (watchdogPackage != pkg) return@Runnable
            val foreground = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (foreground == pkg && ticks < 10) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                ticks++
                mainHandler.postDelayed(tick, 500L)
            } else {
                watchdogPackage = null
            }
        }
        mainHandler.postDelayed(tick, 500L)
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id)
        else (24 * resources.displayMetrics.density).toInt()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        watchdogPackage = null
        mainHandler.removeCallbacksAndMessages(null)
        overlay.hide()
        nsfw.close()
        scope.cancel()
        super.onDestroy()
    }
}
