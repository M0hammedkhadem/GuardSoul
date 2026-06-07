package com.agon.app.consent

import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Caches the current GDPR consent flags so synchronous
 * [AnalyticsManager] / [CrashReporter] guards don't have to
 * call `runBlocking { flow.first() }` from the main thread
 * on every event.
 *
 * Lifecycle:
 *  - Koin creates this as a singleton during application
 *    init (no DataStore access yet, so no blocking).
 *  - [start] launches a coroutine in the application scope
 *    that collects the consent flows and updates the
 *    `analytics` / `crash` fields whenever the user changes
 *    their preferences.
 *  - Readers ([analyticsConsent], [crashConsent]) are
 *    synchronous property accesses — no suspending, no
 *    blocking. Default value (until the first emission lands)
 *    is `false` (opt-out), which is the GDPR-safe default.
 *
 * Why not just use the flows directly? The analytics and
 * crash SDKs call our guards from synchronous Firebase
 * callbacks (e.g. `FirebaseAnalytics.logEvent` is sync). We
 * cannot suspend inside those calls.
 */
class ConsentCache {

    @Volatile
    var analytics: Boolean = false
        private set

    @Volatile
    var crash: Boolean = false
        private set

    /**
     * Begin observing the consent flows. Idempotent — calling
     * twice is a no-op so test / debug restarts don't pile up
     * collectors.
     */
    fun start(scope: CoroutineScope, settings: AppSettings) {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO + SupervisorJob()) {
            settings.consentAnalyticsFlow.distinctUntilChanged().collect { analytics = it }
        }
        scope.launch(Dispatchers.IO + SupervisorJob()) {
            settings.consentCrashFlow.distinctUntilChanged().collect { crash = it }
        }
        Timber.d("ConsentCache: started observing consent flows")
    }

    fun analyticsConsent(): Boolean = analytics

    fun crashConsent(): Boolean = crash

    @Volatile
    private var started: Boolean = false
}
