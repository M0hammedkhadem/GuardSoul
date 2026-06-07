package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.blocking.AiExplorerEngine
import com.agon.app.blocking.BlockCooldownTracker
import com.agon.app.blocking.ContentFilterEngine
import com.agon.app.blocking.ShortstopEngine
import com.agon.app.blocking.UninstallGuardEngine
import com.agon.app.guardianApp
import timber.log.Timber

/**
 * Unified accessibility service.
 *
 * **Why one service instead of two?**
 *
 * The previous design registered two accessibility services from
 * the same app:
 *  - [com.agon.app.services.GuardianAccessibilityService] for
 *    keyword/domain filtering + uninstall protection.
 *  - [com.agon.app.blocking.ShortstopAccessibilityService] for
 *    Reels / Shorts surgical blocking.
 *
 * That worked on AOSP but had real problems in the wild:
 *  - Some OEMs (notably Samsung One UI 5/6 and MIUI 14) refuse to
 *    bind two accessibility services from the same package
 *    simultaneously, or downgrade event delivery for the
 *    "secondary" service. The user would see one feature silently
 *    stop working.
 *  - Both services were observing the same stream of
 *    `AccessibilityEvent`s and each called `rootInActiveWindow`
 *    + `performGlobalAction` independently — duplicated work
 *    and competing `GLOBAL_ACTION_HOME` kicks.
 *  - The "settings say it's enabled" UI was unreliable because
 *    `isServiceEnabled()` had to be called for both classes.
 *
 * This single service is the fix. It is the only one registered
 * in [com.agon.app.AndroidManifest]. Inside the
 * [onAccessibilityEvent] callback it dispatches the event to
 * four engines:
 *  - [ContentFilterEngine] — keyword/domain scan.
 *  - [UninstallGuardEngine] — uninstall protection
 *    (Settings / Phone Manager / destructive buttons).
 *  - [ShortstopEngine] — short-video detection + kick-out.
 *  - [AiExplorerEngine] — on-device NSFW image classification
 *    via `AccessibilityService.takeScreenshot()`. Replaces the
 *    old MediaProjection-based [com.agon.app.AiScannerService].
 *
 * A single shared [bounceCooldown] is constructed here and
 * injected into the engines that perform home-bounce / block
 * actions (content filter + uninstall guard). Sharing the
 * tracker means that if both engines want to fire on the same
 * event burst, only one home animation actually happens —
 * preserving the original `lastBlockTime` cross-handler
 * behavior that lived on the old monolithic [com.agon.app.blocking.GuardianEngine].
 *
 * The engines are otherwise unchanged: each still runs its own
 * early-out checks, cooldowns, and settings subscriptions. The
 * engines never call `performGlobalAction` more than the shared
 * cooldown allows, so there is no kick fight.
 *
 * The service also keeps the legacy contract of a static
 * [current] reference (used by `AccessibilityUtils.isServiceEnabled`
 * and [com.agon.app.utils.BounceHelper]) and registers itself as
 * the global `accessibilityBounceDelegate` so any other code
 * that needs to pop the back stack can do so via
 * `BounceHelper.backToHome`.
 */
class GuardSoulAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: GuardSoulAccessibilityService? = null

        /**
         * The currently bound service instance, or `null` if the
         * service is not bound. Used by `AccessibilityUtils.isServiceEnabled`
         * and the global bounce delegate.
         */
        val current: GuardSoulAccessibilityService? get() = instance
    }

    private lateinit var contentFilter: ContentFilterEngine
    private lateinit var uninstallGuard: UninstallGuardEngine
    private lateinit var shortstop: ShortstopEngine
    private lateinit var aiExplorer: AiExplorerEngine

    /**
     * Shared cooldown for home-bounce / block-screen actions.
     * 1.5 s matches the original [com.agon.app.blocking.GuardianEngine]
     * `BLOCK_COOLDOWN_MS` so cross-handler dedup is preserved.
     */
    private val bounceCooldown = BlockCooldownTracker(cooldownMs = 1500L)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        contentFilter = ContentFilterEngine(this, bounceCooldown).also { it.start() }
        uninstallGuard = UninstallGuardEngine(this, bounceCooldown).also { it.start() }
        shortstop = ShortstopEngine(this).also { it.start() }
        aiExplorer = AiExplorerEngine(this).also { it.start() }
        // Register as the global bounce delegate so any other
        // component can pop the back stack and drop the user on
        // the home screen via `BounceHelper.backToHome`.
        guardianApp()?.accessibilityBounceDelegate = this
        Timber.d("GuardSoulAccessibilityService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        // Clear the global bounce delegate reference so other
        // code falls back to the home intent rather than calling
        // into a dead service.
        if (guardianApp()?.accessibilityBounceDelegate === this) {
            guardianApp()?.accessibilityBounceDelegate = null
        }
        if (::contentFilter.isInitialized) contentFilter.stop()
        if (::uninstallGuard.isInitialized) uninstallGuard.stop()
        if (::shortstop.isInitialized) shortstop.stop()
        // AE-002: aiExplorer uses a TFLite Interpreter. Cancelling
        // its scope is cooperative, so we must join on the in-flight
        // inference job before closing the native handle, otherwise
        // the process can crash mid-read.
        if (::aiExplorer.isInitialized) {
            // Use a per-call scope so the join doesn't depend on
            // service teardown order. stopAndJoin() cancels the
            // engine scope internally; we just wait for it to settle.
            kotlinx.coroutines.runBlocking { aiExplorer.stopAndJoin() }
        }
    }

    /**
     * A11Y-DISABLE-ALL-SERVICES: when the user toggles the
     * accessibility service off in System Settings, the system
     * calls [onUnbind] BEFORE [onDestroy]. We must stop every
     * engine here so:
     *   - the per-event hot path no longer wastes CPU on
     *     accessibility events that never come,
     *   - the AI explorer's TFLite interpreter is released
     *     promptly (we want to drop the ~80 MB heap footprint
     *     the moment the user disables us, not wait for the
     *     system to actually destroy the service), and
     *   - the bounce delegate on [guardianApp] is cleared so
     *     a stale reference cannot survive across a rebind.
     *
     * [onUnbind] returning `true` is the system signal that
     * we've released our resources. We then defer the actual
     * [onDestroy] handling to the normal teardown path.
     */
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Timber.d("GuardSoulAccessibilityService: onUnbind, stopping all engines")
        if (::contentFilter.isInitialized) contentFilter.stop()
        if (::uninstallGuard.isInitialized) uninstallGuard.stop()
        if (::shortstop.isInitialized) shortstop.stop()
        if (::aiExplorer.isInitialized) {
            // Best-effort join. onUnbind runs on the main thread,
            // and we don't want to block the settings UI for the
            // full TFLite release (can take 50-200 ms). Cap with
            // withTimeoutOrNull so the worst case is bounded.
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(500L) {
                    aiExplorer.stopAndJoin()
                }
            }
        }
        if (guardianApp()?.accessibilityBounceDelegate === this) {
            guardianApp()?.accessibilityBounceDelegate = null
        }
        if (instance === this) instance = null
        return true
    }

    override fun onInterrupt() {
        if (::contentFilter.isInitialized) contentFilter.onInterrupt()
        if (::uninstallGuard.isInitialized) uninstallGuard.onInterrupt()
        if (::shortstop.isInitialized) shortstop.onInterrupt()
        if (::aiExplorer.isInitialized) aiExplorer.onInterrupt()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // CF-001: fetch the active root ONCE per event and pass the
        // sealed node to every engine. The four engines previously
        // each called `host.rootInActiveWindow` independently, which
        // was 3 wasted IPC round-trips per a11y event (the cost of
        // `rootInActiveWindow` is non-trivial — it crosses the
        // accessibility service → app process boundary and seals a
        // new AccessibilityNodeInfo per call).
        //
        // The root is only useful for the WINDOW_STATE_CHANGED /
        // WINDOW_CONTENT_CHANGED events. For everything else, we
        // still pass `null` so the engines' early-outs are
        // unaffected.
        val needsRoot = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        val root: AccessibilityNodeInfo? = if (needsRoot) rootInActiveWindow else null
        try {
            // Order matters only marginally here — each engine does
            // its own pre-checks (shield active, package is a
            // target, etc.) before doing real work, so an early-out
            // in one engine does not cost anything in the other. We
            // run the content filter first because its package
            // filter is the cheapest.
            if (::contentFilter.isInitialized) contentFilter.onAccessibilityEvent(event, root)
            if (::uninstallGuard.isInitialized) uninstallGuard.onAccessibilityEvent(event, root)
            if (::shortstop.isInitialized) shortstop.onAccessibilityEvent(event, root)
            if (::aiExplorer.isInitialized) aiExplorer.onAccessibilityEvent(event, root)
        } finally {
            // The host owns the node it fetched; recycle it here so
            // none of the per-engine `try/finally` blocks need to
            // track ownership.
            if (root != null) {
                try { root.recycle() } catch (_: Exception) {}
            }
        }
    }
}
