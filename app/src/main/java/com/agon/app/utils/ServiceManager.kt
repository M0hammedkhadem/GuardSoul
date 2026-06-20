package com.agon.app.utils

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.provider.Settings
import com.agon.app.GuardianApp
import com.agon.app.services.DnsVpnService
import com.agon.app.services.GuardSoulAccessibilityService
import com.agon.app.services.NsfwScannerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

object ServiceManager {

    private const val ACCESSIBILITY_SERVICE = "com.agon.app/com.agon.app.services.GuardSoulAccessibilityService"
    const val REQUEST_VPN_PERMISSION = 1002
    const val REQUEST_VPN_FROM_SHIELD = 1003

    /** Shield toggle: starts/stops GuardSoulAccessibilityService */
    fun setShieldActive(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val componentName = ComponentName(context, GuardSoulAccessibilityService::class.java)
        val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)

        // FIX: Fallback — start/stop the service directly for devices where
        // setComponentEnabledSetting doesn't take effect immediately.
        try {
            val intent = Intent(context, GuardSoulAccessibilityService::class.java)
            if (enabled) {
                context.startService(intent)
            } else {
                context.stopService(intent)
            }
        } catch (e: Exception) {
            Timber.w(e, "Fallback start/stop service failed")
        }

        Timber.i("Shield ${if (enabled) "enabled" else "disabled"}")
    }

    /** Check if AccessibilityService is currently enabled */
    fun isShieldActive(context: Context): Boolean {
        // FIX: Check both component state AND AppSettings for reliability
        val pm = context.packageManager
        val componentName = ComponentName(context, GuardSoulAccessibilityService::class.java)
        val componentEnabled = pm.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        return try {
            val settingsActive = (context.applicationContext as GuardianApp).repository.getAppSettings().isShieldActiveSync()
            componentEnabled || settingsActive
        } catch (e: Exception) {
            componentEnabled
        }
    }

    /** Safe Search toggle: starts/stops DnsVpnService */
    fun setSafeSearchActive(context: Context, enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = (context.applicationContext as GuardianApp).repository.getAppSettings()
            val intent = Intent(context, DnsVpnService::class.java)
            if (enabled) {
                settings.setSafeSearchEnabled(true)
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    val activity = (context.applicationContext as GuardianApp).currentActivity
                    if (activity != null) {
                        withContext(Dispatchers.Main) {
                            activity.startActivityForResult(vpnIntent, REQUEST_VPN_FROM_SHIELD)
                        }
                    } else {
                        Timber.w("VPN consent required but no Activity available")
                        settings.setSafeSearchEnabled(false)
                    }
                    return@launch
                }
                settings.setVpnPermissionGranted(true)
                withContext(Dispatchers.Main) {
                    context.startForegroundService(intent)
                }
                Timber.i("Safe Search enabled - VPN started")
            } else {
                settings.setSafeSearchEnabled(false)
                settings.setVpnPermissionGranted(false)
                context.stopService(intent)
                Timber.i("Safe Search disabled - VPN stopped")
            }
        }
    }

    /** Check if Safe Search VPN is running */
    suspend fun isSafeSearchActive(context: Context): Boolean {
        return (context.applicationContext as GuardianApp).repository.getAppSettings().safeSearchEnabledFlow.first()
    }

    /** AI Scanner toggle: starts/stops NsfwScannerService with MediaProjection */
    fun setAiScannerActive(activity: Activity, enabled: Boolean, requestCode: Int) {
        setAiScannerActiveImpl(activity, enabled, requestCode)
    }

    /** AI Scanner toggle without explicit Activity (uses GuardianApp's current Activity) */
    fun setAiScannerActive(context: Context, enabled: Boolean, requestCode: Int = 1001) {
        val activity = (context.applicationContext as GuardianApp).currentActivity
        if (activity != null) {
            setAiScannerActiveImpl(activity, enabled, requestCode)
        } else {
            Timber.w("AI Scanner: No Activity available - cannot request MediaProjection")
        }
    }

    private fun setAiScannerActiveImpl(activity: Activity, enabled: Boolean, requestCode: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = (activity.applicationContext as GuardianApp).repository.getAppSettings()
            val intent = Intent(activity, NsfwScannerService::class.java)
            if (enabled) {
                // Request MediaProjection permission
                val mpManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                val mpIntent = mpManager.createScreenCaptureIntent()
                settings.setAiExplorerEnabled(true)
                activity.startActivityForResult(mpIntent, requestCode)
                Timber.i("AI Scanner enabled - requesting MediaProjection")
            } else {
                settings.setAiExplorerEnabled(false)
                activity.stopService(intent)
                Timber.i("AI Scanner disabled")
            }
        }
    }

    /** Handle MediaProjection result for AI Scanner */
    fun handleMediaProjectionResult(activity: Activity, resultCode: Int, data: Intent?, requestCode: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = (activity.applicationContext as GuardianApp).repository.getAppSettings()
            if (resultCode == Activity.RESULT_OK && data != null) {
                val mpManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                val mediaProjection = mpManager.getMediaProjection(resultCode, data!!)
                if (mediaProjection != null) {
                    // Start service with the media projection
                    NsfwScannerService.startWithProjection(activity, mediaProjection)
                    Timber.i("AI Scanner started with MediaProjection")
                } else {
                    settings.setAiExplorerEnabled(false)
                    Timber.w("Failed to get MediaProjection")
                }
            } else {
                settings.setAiExplorerEnabled(false)
                Timber.w("MediaProjection permission denied")
            }
        }
    }

    /** Check if AI Scanner is active */
    suspend fun isAiScannerActive(context: Context): Boolean {
        return (context.applicationContext as GuardianApp).repository.getAppSettings().aiExplorerEnabledFlow.first()
    }

    /** Uninstall Protection: triggers DeviceAdmin activation */
    fun activateDeviceAdmin(activity: Activity) {
        activateDeviceAdminImpl(activity)
    }

    /** Uninstall Protection without explicit Activity (uses GuardianApp's current Activity) */
    fun activateDeviceAdmin(context: Context) {
        val activity = (context.applicationContext as GuardianApp).currentActivity
        if (activity != null) {
            activateDeviceAdminImpl(activity)
        } else {
            Timber.w("Device Admin: No Activity available - cannot launch admin settings")
        }
    }

    private fun activateDeviceAdminImpl(activity: Activity) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        val componentName = ComponentName(activity, com.agon.app.admin.GuardianDeviceAdminReceiver::class.java)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(com.agon.app.R.string.device_admin_explanation))
        activity.startActivity(intent)
        Timber.d("Device Admin activation requested")
    }

    /** Check if DeviceAdmin is active */
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val componentName = ComponentName(context, com.agon.app.admin.GuardianDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    /** Start VPN preparation flow (for Safe Search) */
    fun prepareVpn(activity: Activity, requestCode: Int): Boolean {
        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, requestCode)
            return false
        }
        CoroutineScope(Dispatchers.IO).launch {
            (activity.applicationContext as GuardianApp).repository.getAppSettings()
                .setVpnPermissionGranted(true)
        }
        return true
    }

    /** Handle VPN consent result from system dialog */
    fun handleVpnPermissionResult(context: Context, resultCode: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = (context.applicationContext as GuardianApp).repository.getAppSettings()
            if (resultCode == Activity.RESULT_OK) {
                settings.setVpnPermissionGranted(true)
                if (settings.safeSearchEnabledFlow.first()) {
                    withContext(Dispatchers.Main) {
                        context.startForegroundService(Intent(context, DnsVpnService::class.java))
                    }
                }
            } else {
                settings.setVpnPermissionGranted(false)
                settings.setSafeSearchEnabled(false)
            }
        }
    }

    /** Check if VPN is prepared (user has granted permission) */
    fun isVpnPrepared(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }
}