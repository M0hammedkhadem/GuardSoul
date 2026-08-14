package com.agon.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.datastore.preferences.core.edit
import com.agon.app.data.AppBlockState
import com.agon.app.data.PrefKeys
import com.agon.app.data.purityDataStore
import com.agon.app.engine.AppPolicy
import com.agon.app.engine.BlockDecision
import com.agon.app.engine.BlockOverlay
import com.agon.app.engine.BrowserGuard
import com.agon.app.engine.DetectionEngine
import com.agon.app.engine.EngineSettings
import com.agon.app.engine.NsfwClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.Executor

/**
 * The sensory layer of the protection brain.
 *
 * Feeds accessibility events, node trees and throttled screenshots into
 * [DetectionEngine]; executes its [BlockDecision]s (overlay + back/home).
 */
class ProtectionAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val appsSer = MapSerializer(String.serializer(), AppBlockState.serializer())
    private val enginesSer = MapSerializer(String.serializer(), Boolean.serializer())
    private val stringListSer = ListSerializer(String.serializer())

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
        scope.launch {
            purityDataStore.data.collect { p ->
                engine.settings = EngineSettings(
                    shieldActive = p[PrefKeys.SHIELD_ACTIVE] ?: false,
                    aiImageFilter = p[PrefKeys.AI_FILTER] ?: false,
                    appBlocks = p[PrefKeys.APPS]?.let { s ->
                        runCatching { json.decodeFromString(appsSer, s) }.getOrNull()
                    } ?: emptyMap(),
                    searchEngines = p[PrefKeys.ENGINES]?.let { s ->
                        runCatching { json.decodeFromString(enginesSer, s) }.getOrNull()
                    } ?: emptyMap(),
                    contentFilters = p[PrefKeys.FILTERS]?.let { s ->
                        runCatching { json.decodeFromString(enginesSer, s) }.getOrNull()
                    } ?: emptyMap(),
                    blackWords = parseList(p[PrefKeys.BLACK_WORDS]),
                    blackSites = parseList(p[PrefKeys.BLACK_SITES] ?: p[PrefKeys.BLACKLIST]),
                    blackApps = parseList(p[PrefKeys.BLACK_APPS]),
                    whiteSites = parseList(p[PrefKeys.WHITE_SITES] ?: p[PrefKeys.WHITELIST]),
                    whiteApps = parseList(p[PrefKeys.WHITE_APPS]),
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return // never police ourselves
        val now = System.currentTimeMillis()

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            engine.onPackageChanged(pkg)
        }

        // Full app block — checked on EVERY event (cheap map lookup), so the
        // block re-fires even if the user re-opens the app instantly or the
        // previous HOME action raced with app animations.
        engine.checkFullBlock(pkg, now)?.let { execute(it); return }

        // Node-tree based checks (cheap) — run on content/state changes.
        val root = rootInActiveWindow
        val dm = resources.displayMetrics
        engine.checkGenericShorts(root, pkg, dm.widthPixels, dm.heightPixels, now)
            ?.let { execute(it); return }
        engine.checkBrowser(root, pkg, now)?.let { execute(it); return }

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
            // Downsample before inference to keep it light.
            val sample = Bitmap.createScaledBitmap(bmp, 224, 224, true)
            val decision = engine.checkNsfw(sample, now)
            if (sample != bmp) sample.recycle()
            decision?.let { execute(it) }
        }
    }

    private fun execute(decision: BlockDecision) {
        overlay.show(
            title = decision.title,
            message = decision.message,
            autoHideMs = decision.overlayMs,
            buttonLabel = decision.buttonLabel,
            buttonGoesHome = decision.goHome,
        )
        if (decision.goHome) {
            performGlobalAction(GLOBAL_ACTION_HOME)
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
        scope.launch {
            purityDataStore.edit { p ->
                p[PrefKeys.BLOCKS_COUNT] = (p[PrefKeys.BLOCKS_COUNT] ?: 0) + 1
            }
        }
    }

    private fun parseList(raw: String?): List<String> =
        raw?.let { s -> runCatching { json.decodeFromString(stringListSer, s) }.getOrNull() }
            ?: emptyList()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id)
        else (24 * resources.displayMetrics.density).toInt()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlay.hide()
        nsfw.close()
        scope.cancel()
        super.onDestroy()
    }
}
