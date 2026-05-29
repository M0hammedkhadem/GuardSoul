package com.agon.app.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import com.agon.app.AppNotificationChannels
import com.agon.app.GuardianApp
import com.agon.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class CloneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent.data?.encodedSchemeSpecificPart ?: return
                if (isPotentialClone(context, packageName)) {
                    notifyCloneDetected(context, packageName)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    scanAllInstalled(context)
                }
            }
        }
    }

    private fun scanAllInstalled(context: Context) {
        val app = context.applicationContext as GuardianApp
        try {
            val shieldActive = app.repository.getAppSettings().isShieldActive()
            if (!shieldActive) return
        } catch (_: Exception) { return }

        val pm = context.packageManager
        val ourPackage = context.packageName

        val installed = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") pm.getInstalledPackages(0)
            }
        } catch (_: Exception) { return }

        for (info in installed) {
            if (info.packageName == ourPackage) continue
            if (isPotentialClone(context, info.packageName)) {
                notifyCloneDetected(context, info.packageName)
                return
            }
        }
    }

    private fun isPotentialClone(context: Context, packageName: String): Boolean {
        val ourPackage = context.packageName
        if (packageName == ourPackage) return false

        val lower = packageName.lowercase()
        val ourLower = ourPackage.lowercase()

        val suspiciousTokens = listOf("guard", "agon", "shield", "focus", "digital")
        val hasSuspiciousToken = suspiciousTokens.any { it in lower }

        if (!hasSuspiciousToken) return false

        val levenshtein = levenshteinDistance(lower, ourLower)
        if (levenshtein in 1..4) return true

        val nameParts = ourLower.split(".")
        val pkgParts = lower.split(".")
        val common = nameParts.count { it in pkgParts }
        if (common > 0 && common >= nameParts.size - 1) return true

        return false
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun notifyCloneDetected(context: Context, clonePackage: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.tamper_clone_title))
            .setContentText(context.getString(R.string.tamper_clone_text, clonePackage))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.tamper_clone_text, clonePackage)))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(9004, notification)
    }

    companion object {
        fun getIntentFilter(): IntentFilter {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            return filter
        }

        fun getBootFilter(): IntentFilter {
            return IntentFilter(Intent.ACTION_BOOT_COMPLETED)
        }
    }
}
