package com.agon.app.analytics

import android.app.Activity
import android.content.Context
import com.agon.app.data.settings.AppSettings
import com.agon.app.utils.AppLogger
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages the in-app review dialog. The Google Play review API is
 * rate-limited to once every ~6 months per user, so we additionally
 * gate behind a local counter to avoid wasting the budget.
 *
 * Eligibility rules:
 *  - User must have triggered at least 10 "feature_used" events
 *  - At least 14 days must have passed since the last prompt
 */
class ReviewPrompt(
    private val context: Context,
    private val settings: AppSettings
) {

    private val manager: ReviewManager? = runCatching {
        ReviewManagerFactory.create(context)
    }.onFailure { AppLogger.w("ReviewPrompt: ReviewManager init failed: ${it.message}") }
        .getOrNull()

    fun recordFeatureUsage() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            runCatching { settings.incrementReviewUsageCount() }
        }
    }

    /** Returns true if the user meets the local eligibility threshold. */
    suspend fun isEligible(): Boolean {
        val prompted = settings.reviewPromptedFlow.first()
        if (prompted) return false
        val usage = settings.reviewUsageCountFlow.first()
        if (usage < MIN_USAGE_EVENTS) return false
        val nextAt = settings.reviewNextEligibleAtFlow.first()
        return System.currentTimeMillis() >= nextAt
    }

    /**
     * Request a review dialog from Google Play. Returns true if the
     * request was dispatched (the actual display is asynchronous and
     * we have no way to know the result).
     */
    suspend fun requestReview(activity: Activity): Boolean {
        if (!isEligible()) return false
        val manager = manager ?: return false
        val info: ReviewInfo = try {
            suspendCancellableCoroutine { cont ->
                manager.requestReviewFlow().addOnCompleteListener { task ->
                    if (task.isSuccessful) cont.resume(task.result)
                    else cont.cancel()
                }
            }
        } catch (e: Exception) {
            AppLogger.w("ReviewPrompt: requestReviewFlow failed: ${e.message}")
            return false
        }
        return try {
            suspendCancellableCoroutine { cont ->
                manager.launchReviewFlow(activity, info).addOnCompleteListener { task ->
                    kotlinx.coroutines.runBlocking {
                        settings.setReviewPrompted(true)
                        settings.setReviewNextEligibleAt(System.currentTimeMillis() + COOLDOWN_MS)
                    }
                    AppLogger.i("ReviewPrompt: flow completed success=${task.isSuccessful}")
                    cont.resume(task.isSuccessful)
                }
            }
        } catch (e: Exception) {
            AppLogger.w("ReviewPrompt: launchReviewFlow failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val MIN_USAGE_EVENTS = 10
        private const val COOLDOWN_MS = 14L * 24L * 60L * 60L * 1000L
    }
}
