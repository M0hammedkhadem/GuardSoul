package com.agon.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import com.agon.app.guardianApp
import com.agon.app.utils.AppLogger
import kotlinx.coroutines.launch

/**
 * Qustodio-style guard against multi-user / guest-account abuse.
 *
 * On Android the user (or an attacker with a few minutes of access to
 * the lockscreen) can swipe down the notification shade, tap the user
 * avatar, and switch to a freshly-created Guest profile. From there
 * all of GuardSoul's protection is gone, because the data store lives
 * under a different user ID.
 *
 * We:
 *  - watch `Intent.ACTION_USER_FOREGROUND` (the user that just became
 *    active),
 *  - watch `Intent.ACTION_USER_INFO_CHANGED` (a user was added/removed
 *    or its name changed),
 *  - watch `Intent.ACTION_USER_ADDED` and `Intent.ACTION_USER_REMOVED`,
 *  - and on every event inspect the live user count via [UserManager].
 *
 * If multiple user profiles are present we record a tamper event. The
 * AccessibilityService handles the actual app-blocking when the user
 * tries to use an app from the secondary profile.
 */
class UserSwitchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UserSwitchReceiver"
        // The string constants below are intentionally spelled out so
        // we don't depend on the (sometimes hidden) Intent.* aliases.
        private const val ACTION_USER_FOREGROUND = "android.intent.action.USER_FOREGROUND"
        private const val ACTION_USER_INFO_CHANGED = "android.intent.action.USER_INFO_CHANGED"
        private const val ACTION_USER_ADDED = "android.intent.action.USER_ADDED"
        private const val ACTION_USER_REMOVED = "android.intent.action.USER_REMOVED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_USER_FOREGROUND,
            ACTION_USER_INFO_CHANGED,
            ACTION_USER_ADDED,
            ACTION_USER_REMOVED -> Unit
            else -> return
        }

        val app = context.applicationContext.guardianApp() ?: return
        val pendingResult = goAsync()

        app.applicationScope.launch {
            try {
                val um = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return@launch
                val users = um.userProfiles
                val runningCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try { um.userCount } catch (_: Throwable) { users.size }
                } else {
                    users.size
                }

                val hasMultipleUsers = runningCount > 1 || users.size > 1
                if (!hasMultipleUsers) {
                    AppLogger.d(TAG, "Single-user profile detected, no tamper")
                    return@launch
                }

                val settings = app.repository.getAppSettings()
                val shieldActive = settings.isShieldActive()
                if (!shieldActive) {
                    AppLogger.d(TAG, "Shield not active; skipping tamper alert")
                    return@launch
                }

                val reason = when (intent.action) {
                    ACTION_USER_FOREGROUND -> "user_switch"
                    ACTION_USER_ADDED -> "user_added"
                    ACTION_USER_REMOVED -> "user_removed"
                    else -> "user_changed"
                }
                val detail = "Multi-user profile detected. profiles=${users.size} count=$runningCount"
                app.repository.recordTamperAlert(reason, detail)
                AppLogger.w(TAG, "Tamper recorded: $detail")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "User switch check failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
