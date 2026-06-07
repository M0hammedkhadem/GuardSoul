package com.agon.app.blocking

import android.content.Context
import com.agon.app.DnsVpnService
import com.agon.app.PornBlockerService
import com.agon.app.guardianApp
import com.agon.app.services.DeviceOwnerService
import kotlinx.coroutines.flow.first
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
 *   VPN that forces all DNS through the **Family DNS tier**
 *   (OpenDNS FamilyShield primary, Cloudflare for Families
 *   secondary, CleanBrowsing Family tertiary, CleanBrowsing Adult
 *   as final aggressive fallback). Requires user to grant the VPN
 *   permission at least once via `VpnService.prepare()`.
 *   **BATCH-Q**: was the CleanBrowsing Adult filter, which is
 *   too aggressive for a "clean / safe search" experience. The
 *   Family tier is now used by default.
 *
 * The accessibility-service keyword filter runs in parallel regardless of
 * which engine is in use (see
 * [com.agon.app.services.GuardSoulAccessibilityService] and the
 * [com.agon.app.blocking.ContentFilterEngine] it composes).
 */
object PornBlockerController {

    /**
     * Snapshot of the live blocker state. The home screen renders
     * this directly so the user can see *which* engine is currently
     * providing the filter (not just whether the toggle is on).
     */
    data class Status(
        val engine: Engine,
        val isDeviceOwner: Boolean,
        /**
         * **BATCH-Q**: which family DNS provider is currently
         * acting as the primary upstream of the local VPN. `null`
         * when the engine is OFF / KEYWORD_ONLY / PRIVATE_DNS
         * (the DO path uses a hostname, not an IP).
         */
        val familyProvider: com.agon.app.DnsVpnService.FamilyDnsProvider? = null,
        /**
         * **BATCH-Q**: true when SafeSearch is currently
         * enforced by the rewriter (independent of the engine
         * choice — it works for both PRIVATE_DNS and VPN).
         */
        val safeSearchEnforced: Boolean = false,
    ) {
        enum class Engine {
            /** Porn-blocker toggle is off (or shield is off). */
            OFF,
            /**
             * Private DNS is set to CleanBrowsing Family via
             * [PornBlockerService] — strongest, OS-level filter.
             */
            PRIVATE_DNS,
            /**
             * Local VPN is up and routing DNS through the
             * Family DNS tier via [DnsVpnService] — fallback
             * for non-DO installs. The active family
             * provider is reported in [Status.familyProvider].
             */
            VPN,
            /**
             * Neither the DO path nor the VPN is established —
             * e.g. the user has not yet granted VPN consent, or
             * the service is mid-startup. The accessibility keyword
             * filter is still scanning the active window, but
             * DNS-level adult-domain blocking is **not** applied.
             */
            KEYWORD_ONLY,
        }
    }

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

    /**
     * Read-only snapshot of the current engine state. Safe to call
     * from any thread; reads only the static flags exposed by
     * [PornBlockerService] and [DnsVpnService].
     *
     * Used by the home-screen status badge. The freshness is
     * bounded by the ViewModel's polling interval (2 s).
     */
    fun snapshot(ctx: Context): Status {
        val isDo = DeviceOwnerService.isDeviceOwner(ctx)
        val dnsOk = PornBlockerService.isDnsConfigured
        val vpnOk = DnsVpnService.isVpnTunEstablished
        val engine = when {
            dnsOk -> Status.Engine.PRIVATE_DNS
            vpnOk -> Status.Engine.VPN
            isDo && PornBlockerService.wasStoppedIntentionally() -> Status.Engine.OFF
            !isDo && DnsVpnService.wasStoppedIntentionally() -> Status.Engine.OFF
            else -> Status.Engine.KEYWORD_ONLY
        }
        return Status(
            engine = engine,
            isDeviceOwner = isDo,
            // BATCH-Q: expose which family DNS provider is the
            // active primary, so the home screen can label the
            // badge with "OpenDNS FamilyShield" / "Cloudflare
            // for Families" / etc. instead of a generic "VPN".
            familyProvider = if (engine == Status.Engine.VPN) {
                DnsVpnService.cachedActiveFamilyProvider
            } else null,
            // BATCH-Q: SafeSearch is independent of the engine;
            // we read it best-effort from settings. The DataStore
            // .first() returns the first emission (or the
            // default) without subscribing — bounded to a few ms.
            safeSearchEnforced = runCatching {
                runBlocking {
                    val mode = ctx.guardianApp()
                        ?.repository
                        ?.getAppSettings()
                        ?.safeSearchModeFlow
                        ?.first()
                    mode != null && mode != "off"
                }
            }.getOrDefault(false),
        )
    }
}
