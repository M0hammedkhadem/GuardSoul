package com.agon.app.analytics

import android.app.Activity
import android.content.Context
import com.agon.app.utils.AppLogger
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps Google Play's in-app update API. We always prefer flexible
 * updates so the user can keep using the app while the new version
 * downloads in the background.
 */
class InAppUpdater(private val context: Context) {

    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    suspend fun checkForUpdate(activity: Activity): UpdateState {
        val info: AppUpdateInfo = try {
            suspendCancellableCoroutine { cont ->
                manager.appUpdateInfo.addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.cancel() }
            }
        } catch (e: Exception) {
            AppLogger.w("InAppUpdater: check failed: ${e.message}")
            return UpdateState.None
        }
        val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        return when (info.updateAvailability()) {
            UpdateAvailability.UPDATE_AVAILABLE -> {
                try {
                    manager.startUpdateFlowForResult(info, activity, options, UPDATE_REQUEST_CODE)
                } catch (e: Exception) {
                    AppLogger.w("InAppUpdater: startUpdateFlow failed: ${e.message}")
                }
                UpdateState.Available(info)
            }
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                try {
                    manager.startUpdateFlowForResult(info, activity, options, UPDATE_REQUEST_CODE)
                } catch (e: Exception) {
                    AppLogger.w("InAppUpdater: resumeUpdate failed: ${e.message}")
                }
                UpdateState.InProgress
            }
            else -> UpdateState.None
        }
    }

    /** Call from `onResume` to resume a stalled update. */
    fun resumeIfStalled(activity: Activity) {
        val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    manager.startUpdateFlowForResult(info, activity, options, UPDATE_REQUEST_CODE)
                } catch (_: Exception) { }
            }
        }
    }

    sealed class UpdateState {
        data object None : UpdateState()
        data object InProgress : UpdateState()
        data class Available(val info: AppUpdateInfo) : UpdateState()
    }

    companion object {
        const val UPDATE_REQUEST_CODE = 4242
    }
}
