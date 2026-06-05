package com.agon.app.analytics

import android.content.Context
import com.agon.app.utils.AppLogger
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * Facade over Firebase Crashlytics. Centralized so we can:
 *  - gate crash collection behind the GDPR consent flag
 *  - record non-fatal exceptions in a single place
 *  - strip PII from logs attached to crashes
 */
class CrashReporter(
    private val context: Context,
    private val consentProvider: () -> Boolean
) {

    private val crashlytics: FirebaseCrashlytics? = runCatching {
        Firebase.crashlytics
    }.onFailure { AppLogger.w("CrashReporter: FirebaseCrashlytics init failed: ${it.message}") }
        .getOrNull()

    fun setUserIdentifier(id: String) {
        crashlytics?.setUserId(id.take(64))
    }

    fun setCustomKey(key: String, value: String) {
        if (value.length > 64) return
        crashlytics?.setCustomKey(key, value)
    }

    fun log(message: String) {
        if (!consentProvider()) return
        crashlytics?.log(sanitize(message))
    }

    fun recordException(t: Throwable) {
        if (!consentProvider()) return
        crashlytics?.recordException(t)
    }

    fun setCrashCollectionEnabled(enabled: Boolean) {
        crashlytics?.setCrashlyticsCollectionEnabled(enabled)
    }

    private fun sanitize(input: String): String =
        input.replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email]")
            .replace(Regex("(?<![A-Za-z0-9])(\\+?\\d[\\d\\s().-]{7,})"), "[phone]")
            .take(512)
}
