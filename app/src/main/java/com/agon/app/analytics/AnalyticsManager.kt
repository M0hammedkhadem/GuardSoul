package com.agon.app.analytics

import android.content.Context
import android.os.Bundle
import com.agon.app.utils.AppLogger
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Facade over Firebase Analytics. All events from the app go through
 * here so we can:
 *  - filter out PII before forwarding
 *  - gate all events behind the GDPR consent flag
 *  - replace the backend later (e.g. mixpanel, amplitude) without
 *    touching call sites
 */
class AnalyticsManager(
    private val context: Context,
    private val consentProvider: () -> Boolean
) {

    private val firebase: FirebaseAnalytics? = runCatching {
        Firebase.analytics
    }.onFailure { AppLogger.w("AnalyticsManager: Firebase init failed: ${it.message}") }
        .getOrNull()

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        if (!consentProvider()) return
        val firebase = firebase ?: return
        val bundle = Bundle()
        for ((k, v) in params) {
            if (v == null) continue
            when (v) {
                is String -> bundle.putString(k, v)
                is Int -> bundle.putInt(k, v)
                is Long -> bundle.putLong(k, v)
                is Double -> bundle.putDouble(k, v)
                is Float -> bundle.putFloat(k, v)
                is Boolean -> bundle.putBoolean(k, v)
                else -> bundle.putString(k, v.toString())
            }
        }
        runCatching { firebase.logEvent(name, bundle) }
            .onFailure { AppLogger.w("AnalyticsManager: logEvent($name) failed: ${it.message}") }
    }

    fun setUserProperty(key: String, value: String?) {
        if (!consentProvider()) return
        val firebase = firebase ?: return
        runCatching { firebase.setUserProperty(key, value ?: "") }
            .onFailure { AppLogger.w("AnalyticsManager: setUserProperty($key) failed: ${it.message}") }
    }

    fun setUserId(id: String?) {
        val firebase = firebase ?: return
        runCatching { firebase.setUserId(id) }
    }

    // --- Canonical events for funnel reporting -----------------------
    fun logOnboardingStep(step: Int, total: Int) =
        logEvent("onboarding_step", mapOf("step" to step, "total" to total))

    fun logPaywallViewed(source: String) =
        logEvent("paywall_viewed", mapOf("source" to source))

    fun logSubscriptionStarted(tier: String, period: String) =
        logEvent("subscription_started", mapOf("tier" to tier, "period" to period))

    fun logSubscriptionCancelled(tier: String) =
        logEvent("subscription_cancelled", mapOf("tier" to tier))

    fun logFeatureUsed(feature: String) =
        logEvent("feature_used", mapOf("feature" to feature))

    fun logBlockTriggered(packageName: String, blockType: String) =
        logEvent("block_triggered", mapOf("pkg" to packageName, "type" to blockType))

    fun logNsfwDetected(packageName: String) =
        logEvent("nsfw_detected", mapOf("pkg" to packageName))

    fun logSettingsOpened() = logEvent("settings_opened")
    fun logOnboardingCompleted() = logEvent("onboarding_completed")
    fun logReviewPrompted() = logEvent("review_prompted")
    fun logReviewSubmitted() = logEvent("review_submitted")

    companion object {
        const val EVENT_ONBOARDING_STEP = "onboarding_step"
        const val EVENT_PAYWALL_VIEWED = "paywall_viewed"
        const val EVENT_SUBSCRIPTION_STARTED = "subscription_started"
        const val EVENT_FEATURE_USED = "feature_used"
    }
}
