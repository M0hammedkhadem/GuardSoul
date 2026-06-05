package com.agon.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.agon.app.GuardianApp
import com.agon.app.utils.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Intercepts incoming Intents that would open YouTube Shorts, Facebook
 * Reels or Instagram Reels from outside the app (browser, chat apps,
 * notifications, share-sheet) and rewrites them to launch the regular
 * home screen of the target app instead.
 *
 * Why a BroadcastReceiver (not an Activity):
 *   - Zero-latency: the receiver fires before the target Activity has
 *     a chance to render. No "open Shorts → 50 ms later back" flicker.
 *   - 0 false-positives: only literal URL paths / scheme + host that
 *     *are* Shorts/Reels are intercepted. Regular YouTube / Facebook
 *     URLs pass through untouched.
 *
 * Only fires when the user has explicitly enabled partial blocking for
 * the matching package. If `shield` is off or the relevant mode is
 * "off" / "full", the Intent is left alone.
 */
class ShortsIntentReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShortsIntentReceiver"

        // Package + first path-segment pairs that unambiguously identify
        // a Shorts/Reels deep link. Order is irrelevant — we OR-fold.
        private val SHORTS_URL_PATTERNS = listOf(
            "youtube.com/shorts/",
            "www.youtube.com/shorts/",
            "m.youtube.com/shorts/",
            "youtu.be/",              // short URLs; routed through YT app
            "facebook.com/reel/",
            "www.facebook.com/reel/",
            "m.facebook.com/reel/",
            "fb.watch/",              // short FB URLs may resolve to /reel/
            "instagram.com/reel/",
            "www.instagram.com/reel/"
        )

        // YouTube also exposes a custom-scheme deep link, used by share
        // intents from other YouTube clients / extensions.
        private const val YT_CUSTOM_SCHEME = "vnd.youtube"

        private const val READ_SETTINGS_TIMEOUT_MS = 250L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.data?.toString().orEmpty()
        if (data.isEmpty()) return

        val targetPackage = resolveTargetPackage(intent, data) ?: run {
            AppLogger.d(TAG, "Ignored Intent with no target package: $data")
            return
        }

        if (!isShortsOrReelsPath(data)) {
            // Not a Shorts/Reels URL → let Android deliver it normally.
            return
        }

        if (!isBlockingEnabled(context, targetPackage)) {
            AppLogger.d(TAG, "Blocking disabled for $targetPackage, passing through")
            return
        }

        AppLogger.d(TAG, "Intercepted Shorts/Reels URL for $targetPackage: $data")

        // Rewrite the Intent: open the app's launcher (home / main feed)
        // instead of the Shorts surface. NEW_TASK is required because
        // we may not have a task of our own to attach to.
        val home = context.packageManager.getLaunchIntentForPackage(targetPackage)
        if (home == null) {
            AppLogger.w(TAG, "No launch intent for $targetPackage")
            return
        }
        home.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        try {
            context.startActivity(home)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start $targetPackage home intent", e)
        }
    }

    /**
     * Maps the incoming Intent to the social app package it would
     * launch. Handles the YouTube custom scheme and a missing
     * `intent.package` (browser → resolver case) by inspecting the host.
     */
    private fun resolveTargetPackage(intent: Intent, data: String): String? {
        intent.`package`?.let { return it }
        val host = intent.data?.host?.lowercase().orEmpty()
        val scheme = intent.data?.scheme?.lowercase().orEmpty()
        return when {
            scheme == YT_CUSTOM_SCHEME -> "com.google.android.youtube"
            host.contains("youtube") || host.contains("youtu.be") -> "com.google.android.youtube"
            host.contains("facebook") || host.contains("fb.watch") -> "com.facebook.katana"
            host.contains("instagram") -> "com.instagram.android"
            else -> null
        }
    }

    /** Substring match — literal URL check, zero false positives. */
    private fun isShortsOrReelsPath(data: String): Boolean {
        val lower = data.lowercase()
        // Reject YouTube channels (e.g. /@username/shorts) → those are
        // legitimate, only the /shorts/<id> *player* is the target.
        // The check below only matches the player deep links.
        return SHORTS_URL_PATTERNS.any { pattern ->
            lower.contains(pattern)
        }
    }

    /**
     * Reads `shieldActive` + per-package mode from DataStore in a
     * bounded `runBlocking`. We need a synchronous answer because
     * `onReceive` must return quickly; using the DataStore flow
     * async-API would force us to spawn a coroutine and then we
     * couldn't decide whether to start the home activity or not.
     */
    private fun isBlockingEnabled(context: Context, targetPackage: String): Boolean {
        return try {
            runBlocking {
                withTimeoutOrNull(READ_SETTINGS_TIMEOUT_MS) {
                    val app = context.applicationContext as? GuardianApp ?: return@withTimeoutOrNull false
                    val settings = app.repository.getAppSettings()
                    if (!settings.shieldActiveFlow.first()) return@withTimeoutOrNull false
                    when {
                        targetPackage.contains("youtube") ->
                            settings.youtubeModeFlow.first() == "shorts"
                        targetPackage.contains("facebook") ->
                            settings.facebookModeFlow.first() == "reels"
                        targetPackage.contains("instagram") ->
                            settings.instagramModeFlow.first() == "reels"
                        else -> false
                    }
                } ?: false
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Settings read failed; defaulting to allow", t)
            false
        }
    }
}
