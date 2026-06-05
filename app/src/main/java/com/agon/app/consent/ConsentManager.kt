package com.agon.app.consent

import android.app.Activity
import android.content.Context
import com.agon.app.analytics.AnalyticsManager
import com.agon.app.analytics.CrashReporter
import com.agon.app.data.settings.AppSettings
import com.agon.app.utils.AppLogger
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps Google UMP (User Messaging Platform). The flow is:
 *
 *  1. On app start, ask UMP for the current consent state.
 *  2. If required by region (EEA / UK), load and present the form.
 *  3. Persist the resulting decisions in DataStore (so they survive
 *     the process being killed before the SDK returns).
 *  4. Gate Firebase Analytics + Crashlytics on the persisted values.
 */
class ConsentManager(
    private val context: Context,
    private val settings: AppSettings,
    private val analytics: AnalyticsManager,
    private val crash: CrashReporter
) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /**
     * Read the current consent state and present the form if required.
     * Call from `MainActivity.onCreate` AFTER the splash screen and
     * BEFORE the rest of the UI loads.
     */
    suspend fun ensureConsent(activity: Activity, forceEea: Boolean = false) {
        val params = ConsentRequestParameters.Builder()
            .apply {
                if (forceEea) {
                    val debug = ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .build()
                    setConsentDebugSettings(debug)
                }
            }
            .build()

        val requestResult = suspendCancellableCoroutine<FormStatus> { cont ->
            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                { cont.resume(FormStatus.NotRequired) },
                { cont.resume(FormStatus.Error(it.message ?: "unknown")) }
            )
        }
        if (requestResult is FormStatus.Error) {
            AppLogger.w("ConsentManager: UMP update failed: ${requestResult.message}")
            return
        }

        if (consentInformation.isConsentFormAvailable && !settings.consentGdprDecidedFlow.firstBlocking()) {
            val form = suspendCancellableCoroutine<ConsentForm> { cont ->
                UserMessagingPlatform.loadConsentForm(
                    activity,
                    { cont.resume(it) },
                    { err -> cont.resumeWithException(IllegalStateException(err.message)) }
                )
            }
            suspendCancellableCoroutine<Unit> { cont ->
                form.show(activity) {
                    persistDecisions()
                    cont.resume(Unit)
                }
            }
        } else {
            persistDecisions()
        }
    }

    /** Re-prompt the user — used from the settings screen. */
    suspend fun reset() {
        consentInformation.reset()
        settings.setConsentGdprDecided(false)
    }

    /**
     * Apply persisted decisions to the analytics + crash SDKs. Safe to
     * call multiple times.
     */
    fun applyPersistedDecisions() {
        val analyticsOk = settings.consentAnalyticsFlow.firstBlocking()
        val crashOk = settings.consentCrashFlow.firstBlocking()
        analytics.setUserProperty("analytics_consent", if (analyticsOk) "granted" else "denied")
        crash.setCrashCollectionEnabled(crashOk)
    }

    private fun persistDecisions() {
        val status = consentInformation.consentStatus
        val analyticsOk = status == ConsentInformation.ConsentStatus.OBTAINED
        val crashOk = analyticsOk
        runBlocking {
            settings.setConsentGdprDecided(true)
            settings.setConsentAnalytics(analyticsOk)
            settings.setConsentCrash(crashOk)
        }
        analytics.setUserProperty("analytics_consent", if (analyticsOk) "granted" else "denied")
        crash.setCrashCollectionEnabled(crashOk)
        AppLogger.i("ConsentManager: persisted analytics=$analyticsOk crash=$crashOk status=$status")
    }

    private sealed class FormStatus {
        data object NotRequired : FormStatus()
        data class Error(val message: String) : FormStatus()
    }
}

private fun <T> Flow<T>.firstBlocking(): T = runBlocking { first() }
