package com.agon.app.blocking

import android.content.Context
import com.agon.app.DnsVpnService
import com.agon.app.PornBlockerService
import com.agon.app.guardianApp
import com.agon.app.services.DeviceOwnerService
import kotlinx.coroutines.runBlocking

/**
 * Single source of truth for "should the porn blocker be running right now?"
 *
 * Previously the same logic lived in two ViewModels
 * ([com.agon.app.viewmodel.ContentViewModel] and
 * [com.agon.app.viewmodel.HomeViewModel]) and the two implementations
 * disagreed about the edge case "user toggled porn-blocker on while shield was
 * off". This helper ensures the service reacts deterministically to the
 * combined state of [pornBlockerActive] × [shieldActive].
 *
 * The actual blocking engine is one of:
 *
 * - [PornBlockerService] — when the app is installed as **Device Owner**. This
 *   sets the system Private DNS to CleanBrowsing Family Filter via
 *   `DevicePolicyManager.setGlobalSetting`, which works even when the user
 *   later changes the per-app DNS settings.
 *
 * - [DnsVpnService] — for non-Device-Owner installs. This brings up a local
 *   VPN that forces all DNS through CleanBrowsing Adult Filter
 *   (185.228.168.10 / 185.228.169.11). Requires user to grant the VPN
 *   permission at least once via `VpnService.prepare()`.
 *
 * The accessibility-service keyword filter runs in parallel regardless of
 * which engine is in use (see
 * [com.agon.app.services.GuardianAccessibilityService]).
 */
object PornBlockerController {

    /**
     * Start or stop the appropriate engine based on the current persisted
     * settings. Idempotent — safe to call repeatedly.
     */
    fun sync(ctx: Context) {
        val app = ctx.guardianApp() ?: return
        val settings = app.repository.getAppSettings()
        val wantRunning = runCatching {
            runBlocking { settings.isPornBlockerActive() && settings.isShieldActive() }
        }.getOrDefault(false)

        if (!wantRunning) {
            PornBlockerService.stop(ctx)
            DnsVpnService.stop(ctx)
            return
        }

        if (DeviceOwnerService.isDeviceOwner(ctx)) {
            // Device Owner path: Private DNS is the lightest and most reliable
            // option. Stop the VPN if it was running from a previous session.
            PornBlockerService.start(ctx)
            DnsVpnService.stop(ctx)
        } else {
            // Non-Device-Owner path: start the local VPN. This silently no-ops
            // if the user has not yet granted the VPN permission.
            DnsVpnService.start(ctx)
        }
    }
}
