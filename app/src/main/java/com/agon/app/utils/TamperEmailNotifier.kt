package com.agon.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Qustodio-style email notifications for tamper events.
 *
 * We don't send the email in-process (that would need SMTP credentials
 * or a backend). Instead we hand a pre-filled [Intent.ACTION_SEND] to
 * the system; the user just needs to tap "Send" in their mail app.
 * On Android 11+ the system auto-routes to the user's default mail
 * client when the chooser is dismissed.
 *
 * Conditions for sending:
 *   - `parentEmail` is configured
 *   - `remoteMonitoringEnabled` is on (Qustodio only alerts when the
 *     parent has explicitly opted in)
 *
 * The notification is rate-limited via [MIN_INTERVAL_MS] so a burst of
 * tamper events (e.g. user spamming the Settings button) doesn't flood
 * the chooser.
 */
object TamperEmailNotifier {

    private const val TAG = "TamperEmailNotifier"
    private const val PREFS = "tamper_email"
    private const val KEY_LAST_SENT = "last_sent_ms"

    /** Minimum time between two consecutive email intents. */
    private const val MIN_INTERVAL_MS = 5L * 60L * 1_000L   // 5 min

    fun maybeNotify(
        context: Context,
        scope: CoroutineScope,
        settings: AppSettings,
        tamperType: String,
        tamperDetail: String
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val email = settings.parentEmailFlow.first().trim()
                if (email.isEmpty()) return@launch
                val monitoring = settings.remoteMonitoringEnabledFlow.first()
                if (!monitoring) return@launch

                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val last = prefs.getLong(KEY_LAST_SENT, 0L)
                val now = System.currentTimeMillis()
                if (now - last < MIN_INTERVAL_MS) {
                    AppLogger.d(TAG, "Skipping email (rate-limited, last sent $last)")
                    return@launch
                }
                prefs.edit().putLong(KEY_LAST_SENT, now).apply()

                val subject = "GuardSoul: tamper detected ($tamperType)"
                val body = buildString {
                    append("A tamper event was detected on your device.\n\n")
                    append("Type: ").append(tamperType).append('\n')
                    append("Detail: ").append(tamperDetail).append('\n')
                    append("Time: ").append(java.util.Date(now).toString()).append('\n')
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "Send tamper alert")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(chooser)
                } catch (t: Throwable) {
                    AppLogger.w(TAG, "No email app installed; falling back to SEND", t)
                    val fallback = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(
                        Intent.createChooser(fallback, "Send tamper alert")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } catch (t: Throwable) {
                AppLogger.w(TAG, "Failed to send tamper email", t)
            }
        }
    }
}
