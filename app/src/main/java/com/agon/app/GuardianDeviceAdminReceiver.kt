package com.agon.app

import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.flow.first
import androidx.core.app.NotificationCompat
import com.agon.app.data.settings.AppSettings
import com.agon.app.data.settings.EncryptedPrefs
import com.agon.app.utils.KnoxManager
import com.agon.app.utils.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import android.widget.Toast

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val PREFS_NAME = "guardianship"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_BLOCK_SAFE_MODE = "block_safe_mode"

        fun isProtectionEnabled(context: Context): Boolean {
            return try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_PROTECTION_ENABLED, false)
            } catch (_: Exception) { false }
        }

        fun setProtectionEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PROTECTION_ENABLED, enabled)
                .apply()
        }

        fun isAdminActive(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
                dpm.isAdminActive(component)
            } catch (_: Exception) { false }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        setProtectionEnabled(context, true)
        scope.launch {
            try {
                val app = context.applicationContext as GuardianApp
                app.repository.getAppSettings().setPermAdmin(true)
                KnoxManager.activateKnoxLicense(context)
            } catch (e: Exception) {
                Timber.w(e, "GuardianDeviceAdminReceiver: post-enable setup failed")
            }
        }
        Toast.makeText(context, R.string.device_admin_enabled_toast, Toast.LENGTH_SHORT).show()
        Timber.d("GuardianDeviceAdminReceiver: enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        scope.launch {
            try {
                val app = context.applicationContext as GuardianApp
                val settings = app.repository.getAppSettings()
                val encryptedPrefs = EncryptedPrefs(context)
                val hasPin = encryptedPrefs.hasPin()
                val protectionEnabled = isProtectionEnabled(context)
                val isStrongProtection = try { context.getSharedPreferences("guardianship", Context.MODE_PRIVATE).getBoolean("protection_enabled", false) } catch (_: Exception) { false }
                if (protectionEnabled || isStrongProtection) {
                    recordTamperAlert(context, "device_admin_disabled",
                        "Device Admin was disabled while protection was active. hasPin=$hasPin strongProtection=$isStrongProtection")

                    showTamperNotification(context, "device_admin_disabled")

                    if (hasPin) {
                        Toast.makeText(context, R.string.tamper_admin_re_enable_hint, Toast.LENGTH_LONG).show()
                    }

                    Timber.w("GuardianDeviceAdminReceiver: disabled while protected! hasPin=$hasPin")
                }
                settings.setPermAdmin(false)
            } catch (e: Exception) {
                Timber.w(e, "GuardianDeviceAdminReceiver: onDisabled error")
            }
        }
        setProtectionEnabled(context, false)
        Toast.makeText(context, R.string.device_admin_disabled_toast, Toast.LENGTH_SHORT).show()
        Timber.w("GuardianDeviceAdminReceiver: disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val app = context.applicationContext as GuardianApp
        val result = runBlocking {
            try {
                val settings = app.repository.getAppSettings()
                val encryptedPrefs = EncryptedPrefs(context)
                val hasPin = encryptedPrefs.hasPin()
                val isStrongProtection = try { context.getSharedPreferences("guardianship", Context.MODE_PRIVATE).getBoolean("protection_enabled", false) } catch (_: Exception) { false }

                if (isStrongProtection) {
                    recordTamperAlert(context, "disable_attempt",
                        "Attempt to disable Device Admin while strong protection is enabled. hasPin=$hasPin")
                    showTamperNotification(context, "disable_attempt")
                }

                if (hasPin) {
                    context.getString(R.string.device_admin_disable_pin_warning)
                } else {
                    context.getString(R.string.device_admin_disable_warning)
                }
            } catch (_: Exception) {
                context.getString(R.string.device_admin_disable_warning)
            }
        }
        return result
    }

    fun verifyPinBeforeDisable(context: Context, pin: String): Boolean {
        val encryptedPrefs = EncryptedPrefs(context)
        return SecurityUtils.verifyPin(pin, encryptedPrefs)
    }

    private fun isPackageUser401(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pm = context.packageManager
                val app = pm.getApplicationInfo(packageName, 0)
                app.uid % 100000 == 401
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isAdminUser(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val um = context.getSystemService(Context.USER_SERVICE) as UserManager
                um.isAdminUser()
            } else {
                @Suppress("DEPRECATION")
                val um = context.getSystemService(Context.USER_SERVICE) as UserManager
                um.isAdminUser()
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun recordTamperAlert(context: Context, type: String, detail: String) {
        scope.launch {
            try {
                val app = context.applicationContext as GuardianApp
                app.repository.recordTamperAlert(
                    type = type,
                    detail = "$detail (timestamp=${System.currentTimeMillis()})"
                )
                if (app.repository.getAppSettings().isRemoteMonitoringEnabled()) {
                    app.repository.sendAlert("tamper", detail)
                }
            } catch (e: Exception) {
                Timber.w(e, "GuardianDeviceAdminReceiver: failed to record tamper alert")
            }
        }
    }

    private fun showTamperNotification(context: Context, reason: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = when (reason) {
            "disable_attempt" -> context.getString(R.string.tamper_disable_attempt_title)
            "device_admin_disabled" -> context.getString(R.string.tamper_admin_disabled_title)
            "pin_failed" -> context.getString(R.string.tamper_pin_failed_title)
            else -> context.getString(R.string.tamper_admin_disabled_title)
        }
        val text = when (reason) {
            "disable_attempt" -> context.getString(R.string.tamper_disable_attempt_text)
            "device_admin_disabled" -> context.getString(R.string.tamper_admin_disabled_text)
            "pin_failed" -> context.getString(R.string.tamper_pin_failed_text)
            else -> context.getString(R.string.tamper_admin_disabled_text)
        }
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
        manager.notify(9005, notification)
    }

    override fun onUserAdded(context: Context, intent: Intent, userHandle: UserHandle) {
        super.onUserAdded(context, intent, userHandle)
        scope.launch {
            try {
                val hasPin = EncryptedPrefs(context).hasPin()
                val protectionEnabled = isProtectionEnabled(context)
                if (protectionEnabled && hasPin) {
                    recordTamperAlert(context, "user_added",
                        "A new user was added to the device while protection is active")
                    showTamperNotification(context, "user_added")
                }
            } catch (e: Exception) {
                Timber.w(e, "GuardianDeviceAdminReceiver: onUserAdded error")
            }
        }
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Timber.d("GuardianDeviceAdminReceiver: profile provisioning complete")
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Timber.d("GuardianDeviceAdminReceiver: password changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        scope.launch {
            try {
                val encryptedPrefs = EncryptedPrefs(context)
                if (encryptedPrefs.hasPin() && isProtectionEnabled(context)) {
                    recordTamperAlert(context, "password_failed",
                        "Device password failed while protection is active")
                }
            } catch (e: Exception) {
                Timber.w(e, "GuardianDeviceAdminReceiver: onPasswordFailed error")
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
    }
}
