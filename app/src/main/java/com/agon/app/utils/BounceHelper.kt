package com.agon.app.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.agon.app.guardianApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Bulldog Blocker's "back-button trick" + Canopy's "forced exit".
 *
 * The Android system gives foreground apps a *very* hard time
 * reaching the home screen from a background service. Two ways
 * actually work in practice on modern Android:
 *
 *  1. The standard `Intent.ACTION_MAIN / CATEGORY_HOME` — this
 *     just brings up the launcher, but the user can be dropped
 *     back onto the previous task when they tap Recents.
 *  2. `AccessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)`
 *     followed by `HOME`. This is *guaranteed* to evict the
 *     current screen because the back-stack gets popped first.
 *
 * The AccessibilityService is the only API that exposes
 * `GLOBAL_ACTION_*`. We can't call it from a regular Service, so
 * this helper falls back to the standard home intent when no
 * accessibility service is bound (rare in GuardSoul, but possible
 * in the first few seconds after boot).
 *
 * For the "back button trick" pattern we:
 *   1. send BACK once → pop the current screen
 *   2. delay 80 ms → let the back animation finish
 *   3. send HOME → drop the task entirely
 *   4. (optional) send BACK twice if the app is still foreground
 *      after 250 ms.
 */
object BounceHelper {

    private const val TAG = "BounceHelper"
    private const val BACK_TO_HOME_DELAY_MS = 80L
    private const val SECOND_PASS_DELAY_MS = 250L

    /**
     * Camera apps that can be used to *create* sensitive content
     * (photos / videos). When the AI scanner detects sensitive
     * content while one of these is in the foreground, the
     * "forced image removal" pattern kicks in: we bounce the user
     * out so the image is never persisted to disk. Canopy uses
     * the same heuristic.
     *
     * The list is intentionally conservative — we only block the
     * stock camera apps, not the camera *inside* social media
     * apps (those are blocked by the generic sensitive-content
     * flow).
     */
    val CAMERA_PACKAGES: Set<String> = setOf(
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.GoogleCamera",
        "com.google.android.camera",
        "com.sec.android.app.camera",
        "com.huawei.camera",
        "com.miui.camera",
        "com.oppo.camera",
        "com.vivo.camera",
        "com.oneplus.camera",
        "com.sonyericsson.android.camera",
        "com.htc.camera",
        "com.lge.camera",
        "com.motorola.camera",
    )

    fun isCameraPackage(pkg: String?): Boolean = pkg != null && pkg in CAMERA_PACKAGES

    /**
     * Try to force the user out of the current foreground app. Safe
     * to call from any coroutine context. The function is best-effort:
     * if the accessibility service isn't bound yet, we fall back to
     * the platform `HOME` intent which is guaranteed to at least
     * bring up the launcher.
     */
    fun backToHome(context: Context) {
        val app = context.applicationContext.guardianApp()
        val svc = app?.accessibilityBounceDelegate
        if (svc != null) {
            // Preferred: pop the back stack then go home.
            svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                delay(BACK_TO_HOME_DELAY_MS)
                svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                // Second pass: if a confirmation dialog is still up, try
                // back again to dismiss it.
                delay(SECOND_PASS_DELAY_MS)
                svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            return
        }
        // Fallback: home intent.
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(home)
        } catch (t: Throwable) {
            Timber.w(TAG, "Could not bounce user to home", t)
        }
    }
}
