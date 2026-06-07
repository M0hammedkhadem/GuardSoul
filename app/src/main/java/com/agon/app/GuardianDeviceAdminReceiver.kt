package com.agon.app

import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserHandle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.agon.app.data.settings.EncryptedPrefs
import com.agon.app.utils.KnoxManager
import com.agon.app.utils.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    // Issue #169: Use a dedicated scope for the receiver
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Issue #138: All protection states now reside in EncryptedPrefs.
        // Call sites route through [guardianEncryptedPrefs] so they
        // share the same singleton instance that `pinHashFlow` listeners
        // registered against. Constructing a fresh `EncryptedPrefs(context)`
        // would mutate a different SharedPreferences handle and leave
        // the listener registry stale.
        fun isProtectionEnabled(context: Context): Boolean {
            return context.guardianEncryptedPrefs()?.isProtectionEnabled() ?: false
        }

        fun setProtectionEnabled(context: Context, enabled: Boolean) {
            context.guardianEncryptedPrefs()?.setProtectionEnabled(enabled)
        }

        fun isAdminActive(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
                dpm.isAdminActive(component)
            } catch (_: Exception) { false }
        }

        fun verifyPinBeforeDisable(context: Context, pin: String): Boolean {
            val storedHash = context.guardianEncryptedPrefs()?.getPinHash().orEmpty()
            if (storedHash.isBlank()) return true
            return SecurityUtils.verifyPinAgainstHash(pin, storedHash)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        setProtectionEnabled(context, true)

        val app = context.applicationContext.guardianApp()
        scope.launch {
            try {
                app?.repository?.getAppSettings()?.setPermAdmin(true)
                KnoxManager.activateKnoxLicense(context)
            } catch (e: Exception) {
                Timber.w(e, "GuardianDeviceAdminReceiver: setup failed")
            }
        }
        Toast.makeText(context, R.string.tamper_admin_enabled_toast, Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val encryptedPrefs = context.guardianEncryptedPrefs()
        val protectionActive = encryptedPrefs?.isProtectionEnabled() == true ||
            encryptedPrefs?.isStrongProtection() == true

        if (protectionActive) {
            recordTamperAlert(context, "device_admin_disabled",
                "Critical: Device Admin disabled while protection was active.")
            showTamperNotification(context, "device_admin_disabled")
        }

        val app = context.applicationContext.guardianApp()
        scope.launch {
            app?.repository?.getAppSettings()?.setPermAdmin(false)
        }

        encryptedPrefs?.setProtectionEnabled(false)
        Toast.makeText(context, R.string.tamper_admin_disabled_toast, Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Issue #128 & #173: Direct synchronous read from encryptedPrefs (Thread-safe)
        val encryptedPrefs = context.guardianEncryptedPrefs()
        val hasPin = encryptedPrefs?.hasPin() == true

        if (encryptedPrefs?.isStrongProtection() == true) {
            recordTamperAlert(context, "disable_attempt", "Unauthorized attempt to disable Admin.")
            showTamperNotification(context, "disable_attempt")
        }

        return if (hasPin) {
            context.getString(R.string.device_admin_disable_pin_warning)
        } else {
            context.getString(R.string.device_admin_disable_warning)
        }
    }

    private fun recordTamperAlert(context: Context, type: String, detail: String) {
        val app = context.applicationContext.guardianApp()
        scope.launch {
            try {
                app?.repository?.recordTamperAlert(type, detail)
                if (app?.repository?.getAppSettings()?.isRemoteMonitoringEnabled() == true) {
                    app.repository.sendAlert("tamper", detail)
                }
            } catch (_: Exception) {}
        }
    }

    private fun showTamperNotification(context: Context, reason: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = when (reason) {
            "disable_attempt" -> context.getString(R.string.tamper_disable_attempt_title)
            "device_admin_disabled" -> context.getString(R.string.tamper_admin_disabled_title)
            else -> context.getString(R.string.tamper_admin_disabled_title)
        }
        
        val notification = NotificationCompat.Builder(context, "tamper_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.tamper_admin_disabled_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()
            
        // Issue #248: Use unique IDs to prevent notification overwriting
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
