package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.R
import com.agon.app.guardianApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Uninstall-protection engine.
 *
 * Other half of the original [com.agon.app.blocking.GuardianEngine]
 * (split into [ContentFilterEngine] and [UninstallGuardEngine] so
 * the unified [com.agon.app.services.GuardSoulAccessibilityService]
 * can compose them as small, single-purpose engines alongside
 * [ShortstopEngine] and [AiExplorerEngine]).
 *
 * Responsibilities — only when the user has uninstall protection on
 * (or strong protection is on):
 *  - bounce the user off the system Settings page if they navigate
 *    to GuardSoul's App Info,
 *  - bounce them off any OEM "Phone Manager" / "Security Center"
 *    app that ships its own force-stop / uninstall workflow
 *    (Qustodio-style),
 *  - catch destructive buttons (Force stop / Clear data / Disable /
 *    Uninstall) on App Info pages that don't carry the usual
 *    `Settings$*` class name.
 *
 * The engine never touches keyword or domain content — that lives
 * in [ContentFilterEngine]. A shared [bounceCooldown] is injected
 * by the host service so the two engines don't fire competing
 * home animations on the same event.
 */
class UninstallGuardEngine(
    private val host: AccessibilityService,
    private val bounceCooldown: BlockCooldownTracker,
) {

    companion object {
        /**
         * Settings-related package names across OEMs. The uninstall guard
         * is only effective on these; any other package is ignored.
         */
        private val KNOWN_SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.miui.securitycenter",
            "com.miui.settings",
            "com.samsung.android.settings",
            "com.huawei.systemmanager",
            "com.coloros.settings",
            "com.oppo.settings",
        )

        /**
         * Phone Manager / device-care apps that ship a "force-stop" or
         * "uninstall" workflow that doesn't go through the AOSP Settings
         * App Info screen. Qustodio blocks all of these while
         * uninstall-protection is on — we mirror that. Each entry has
         * been seen in the wild on at least one OEM.
         */
        private val PHONE_MANAGER_PACKAGES = setOf(
            // Xiaomi / MIUI / Redmi / Poco
            "com.miui.securitycenter",
            "com.miui.managecenter",
            "com.miui.cleanmaster",
            "com.xiaomi.gamecenter",
            "com.xiaomi.joyose",
            // Huawei / Honor
            "com.huawei.systemmanager",
            "com.huawei.hwireader",
            "com.huawei.intelligent",
            "com.hihonor.systemmanager",
            // Samsung
            "com.samsung.android.lool",
            "com.samsung.android.sm_cn",
            "com.samsung.android.sm.dev",
            // OPPO / Realme / OnePlus / ColorOS
            "com.coloros.safecenter",
            "com.coloros.oppoguardelf",
            "com.oppo.safe",
            "com.realme.securitycenter",
            "com.oneplus.security",
            // Vivo
            "com.vivo.permissionmanager",
            "com.iqoo.secure",
            // Lenovo / Motorola / Asus
            "com.lenovo.safecenter",
            "com.asus.mobilemanager",
            "com.motorola.security",
            // ZTE / Nubia
            "com.zte.heartyservice",
            // Generic AOSP
            "com.android.systemui",
        )

        /**
         * Visible button labels that indicate the user is one tap away
         * from breaking GuardSoul. Matched case-insensitively against
         * the concatenated text of the foreground window.
         */
        private val DESTRUCTIVE_ACTION_KEYWORDS = listOf(
            "Force stop", "Force Stop", "إيقاف قسري",
            "Clear data", "مسح البيانات",
            "Clear cache", "مسح ذاكرة التخزين المؤقت",
            "Uninstall", "إلغاء التثبيت",
            "Disable", "تعطيل",
            "Disable app", "تعطيل التطبيق",
            "Uninstall updates", "إلغاء تحديثات",
            "Storage & cache", "التخزين وذاكرة التخزين المؤقت",
        )
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * CF-001: the AccessibilityNodeInfo that the host
     * ([GuardSoulAccessibilityService]) resolved for the current
     * event. The host owns the node and recycles it; helpers in
     * this engine read the field instead of calling
     * `host.rootInActiveWindow` independently. Reset to null after
     * each event.
     */
    private var currentRoot: AccessibilityNodeInfo? = null

    private val repo by lazy {
        host.applicationContext.guardianApp()?.repository
            ?: throw IllegalStateException("Repo not found")
    }

    /**
     * True while the user has installed GuardSoul as Device Admin and
     * either the uninstall-protection toggle or strong protection is on.
     * While this is on we watch the foreground Settings window and kick
     * the user back to home if they navigate to GuardSoul's App Info page.
     */
    @Volatile private var cachedUninstallProtection = false

    /** Subscribe to settings flows. Called from the host. */
    fun start() {
        ioScope.launch { observeSettings() }
    }

    /** Unsubscribe and cancel the ioScope. Called from the host. */
    fun stop() {
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private suspend fun observeSettings() = coroutineScope {
        val settings = repo.getAppSettings()

        // FEATURES_SPEC §5: watch the uninstall-protection flag and
        // block attempts to reach GuardSoul's App Info screen.
        launch {
            settings.uninstallProtectionFlow.collect { cachedUninstallProtection = it }
        }
    }

    /** Main event entry point — called by the host service. */
    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        preFetchedRoot: AccessibilityNodeInfo? = null,
    ) {
        if (!cachedUninstallProtection) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == host.packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // CF-001: stash the host-provided root in a transient
            // field so the per-branch helpers below can use it
            // without needing a new parameter on every call. The
            // field is reset in the `finally` so a stale node from
            // a previous event is never reused.
            val previousRoot = currentRoot
            currentRoot = preFetchedRoot
            try {
                // Uninstall protection guard. The Settings app's
                // window class name for "App Info" varies across
                // OEMs ("Settings$AppDetailsActivity" on AOSP,
                // "SubSettings" on Samsung, "ManageApplications" on
                // some MIUI builds). We watch for any of those +
                // the Settings package and confirm via a text scan
                // that GuardSoul is the target app before kicking
                // the user out.
                handleUninstallProtectionAttempt(event, pkg)
                // Qustodio-style: also block the OEM "Phone
                // Manager" apps that ship their own force-stop /
                // uninstall workflow.
                handlePhoneManagerAttempt(event, pkg)
                // Heavier: catch destructive buttons (Force stop /
                // Clear data / Disable / Uninstall) on App Info
                // pages that don't carry the usual class name.
                handleForceStopOrClearDataAttempt(event, pkg)
            } finally {
                currentRoot = previousRoot
            }
        }
    }

    /** Called from the host when the framework interrupts us. */
    fun onInterrupt() {
        // No overlay to dismiss in this engine.
    }

    /**
     * Called from [onAccessibilityEvent] when the user has uninstall
     * protection turned on and the foreground window appears to be the
     * system Settings app. We look for a GuardSoul marker in the visible
     * text; if present, we record a tamper alert and bounce the user back
     * to the home screen.
     */
    private fun handleUninstallProtectionAttempt(event: AccessibilityEvent, pkg: String) {
        // Settings package names vary across OEMs. AOSP and most modern
        // Android skins funnel App Info through com.android.settings, but
        // MIUI / Xiaomi routes it through com.miui.securitycenter or
        // com.miui.settings, and some Samsung builds use
        // com.samsung.android.settings. The full list lives in
        // [KNOWN_SETTINGS_PACKAGES] so the lookup is allocation-free.
        if (pkg !in KNOWN_SETTINGS_PACKAGES) return
        val className = event.className?.toString() ?: return
        if (!className.contains("Settings", ignoreCase = true) &&
            !className.contains("ManageApplications", ignoreCase = true) &&
            !className.contains("AppInfo", ignoreCase = true) &&
            !className.contains("ApplicationDetails", ignoreCase = true)) {
            return
        }
        // CF-001: prefer the host's pre-fetched root (the
        // `currentRoot` field set by onAccessibilityEvent); only
        // call `host.rootInActiveWindow` if the host didn't supply
        // one.
        val ownedRoot = if (currentRoot == null) host.rootInActiveWindow else null
        val root = currentRoot ?: ownedRoot ?: return
        try {
            val text = AccessibilityTreeUtils.extractAllText(root)
            if (text.isBlank()) return
            val targetIsGuardian = text.contains("com.agon.app", ignoreCase = true) ||
                text.contains("GuardSoul", ignoreCase = true) ||
                text.contains(host.getString(R.string.app_name), ignoreCase = true)
            if (targetIsGuardian) {
                if (!bounceCooldown.tryFire()) return
                ioScope.launch {
                    try {
                        repo.recordTamperAlert(
                            "uninstall_attempt",
                            "User opened GuardSoul's App Info page while uninstall protection was on."
                        )
                    } catch (_: Exception) {}
                }
                // Bounce to home — the user can reopen GuardSoul from the
                // launcher, but they can't reach the App Info / Uninstall
                // button through Settings.
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
        } finally {
            // CF-001: only recycle nodes we allocated. The host
            // owns `currentRoot` (set by onAccessibilityEvent).
            if (ownedRoot != null) {
                try { ownedRoot.recycle() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Qustodio-style guard: block the OEM "Phone Manager" / "Security
     * Center" apps that can be used to force-stop or uninstall any
     * package, including us. We treat any window from a known Phone
     * Manager package as an attempt to tamper with the device and
     * bounce the user back to home while recording a tamper alert.
     *
     * The shared [bounceCooldown] suppresses duplicates if the user
     * keeps trying.
     */
    private fun handlePhoneManagerAttempt(event: AccessibilityEvent, pkg: String) {
        if (pkg !in PHONE_MANAGER_PACKAGES) return
        if (!bounceCooldown.tryFire()) return

        val appLabel = getAppLabel(pkg)
        ioScope.launch {
            try {
                repo.recordTamperAlert(
                    "phone_manager_opened",
                    "Opened $appLabel ($pkg) while uninstall protection was on."
                )
            } catch (_: Exception) {}
        }
        host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * Heavier guard: when the user is inside an App Info page and the
     * visible screen contains *both* a GuardSoul marker and one of the
     * destructive buttons (Force stop / Clear data / Uninstall / Disable),
     * bounce the user back to home even if the class name doesn't match
     * the AOSP `Settings$*` pattern. This catches OEM-specific
     * confirm-dialog screens that don't carry the usual class name but
     * still expose a clickable destructive action.
     *
     * Only triggered on Settings-related packages (already filtered
     * upstream) and only when [cachedUninstallProtection] is on.
     */
    private fun handleForceStopOrClearDataAttempt(event: AccessibilityEvent, pkg: String) {
        if (pkg !in KNOWN_SETTINGS_PACKAGES) return
        val root = host.rootInActiveWindow ?: return
        try {
            val text = AccessibilityTreeUtils.extractAllText(root)
            if (text.isBlank()) return
            val guardian =
                text.contains("com.agon.app", ignoreCase = true) ||
                text.contains("GuardSoul", ignoreCase = true) ||
                text.contains(host.getString(R.string.app_name), ignoreCase = true)
            if (!guardian) return
            val destructive = DESTRUCTIVE_ACTION_KEYWORDS.any { text.contains(it, ignoreCase = true) }
            if (!destructive) return

            if (!bounceCooldown.tryFire()) return

            ioScope.launch {
                try {
                    repo.recordTamperAlert(
                        "destructive_action",
                        "Destructive action button visible for GuardSoul: ${text.take(160)}"
                    )
                } catch (_: Exception) {}
            }
            host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } finally {
            root.recycle()
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val ai = host.packageManager.getApplicationInfo(pkg, 0)
            host.packageManager.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) { pkg }
    }
}
