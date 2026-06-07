package com.agon.app

import android.app.Application
import android.content.Context
import com.agon.app.analytics.CrashReporter
import com.agon.app.billing.BillingManager
import com.agon.app.data.remote.FirebaseSyncWorker
import com.agon.app.data.repository.AppRepository
import com.agon.app.di.appModules
import com.agon.app.utils.CategoryRegistry
import org.koin.android.ext.android.inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.agon.app.logging.ReleaseTree
import com.agon.app.consent.ConsentCache
import com.agon.app.consent.ConsentManager
import com.agon.app.account.CloudSyncRepository
import com.agon.app.blocking.AiBlockTracker
import timber.log.Timber

fun Context.guardianApp(): GuardianApp? {
    return applicationContext as? GuardianApp
}

/**
 * Returns the Koin-managed [com.agon.app.data.settings.EncryptedPrefs]
 * singleton for this process. Call sites that previously wrote
 * `EncryptedPrefs(context)` ad-hoc should use this helper instead —
 * constructing a fresh instance mutates a different SharedPreferences
 * handle and bypasses the listener registry (so `pinHashFlow` never
 * hears the change).
 */
fun Context.guardianEncryptedPrefs(): com.agon.app.data.settings.EncryptedPrefs? {
    return guardianApp()?.repository?.getAppSettings()?.encryptedPrefs
}

class GuardianApp : Application() {
    val repository: AppRepository by inject()
    val billingManager: BillingManager by inject()
    val crashReporter: CrashReporter by inject()
    val consentManager: ConsentManager by inject()
    val consentCache: ConsentCache by inject()
    val cloudSync: CloudSyncRepository by inject()
    val aiBlockTracker: AiBlockTracker by inject()

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    var accessibilityBounceDelegate: android.accessibilityservice.AccessibilityService? = null

    override fun onCreate() {
        super.onCreate()

        LanguageManager.load(this)

        startKoin {
            androidContext(this@GuardianApp)
            modules(appModules)
        }

        if (BuildConfig.IS_DEBUG_BUILD) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        AppNotificationChannels.createAll(this)

        // Apply persisted GDPR consent decisions to the SDKs.
        consentManager.applyPersistedDecisions()

        // Start observing the consent flows so the (synchronous)
        // AnalyticsManager / CrashReporter guards can read the
        // current values without blocking on DataStore.
        consentCache.start(applicationScope, repository.getAppSettings())

        // Start the billing client lazily — connects on first product
        // query. We just prime the in-memory state.
        billingManager.start()

        applicationScope.launch(Dispatchers.IO) {
            seedDefaultBlocklists()
            seedCategoryDefaults()
        }

        applicationScope.launch(Dispatchers.IO) {
            try {
                val settings = repository.getAppSettings()
                if (settings.isRemoteMonitoringEnabled() && settings.isShieldActive()) {
                    FirebaseSyncWorker.schedule(this@GuardianApp)
                    repository.syncToFirebase()
                    repository.processRemoteCommands()
                }
            } catch (e: Exception) {
                Timber.e(e, "GuardianApp: Firebase init failed")
            }
        }
    }

    private suspend fun seedDefaultBlocklists() {
        try {
            val existing = repository.getBlocklist("blacklist", "keywords")
            if (existing.isNotEmpty()) return

            val defaultKeywords = listOf(
                "porn", "xxx", "sex", "nude", "naked", "hentai",
                "adult", "erotic", "nsfw", "fetish",
                "إباحي", "جنس", "عاري"
            )
            val defaultWebsites = listOf(
                "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com",
                "youporn.com", "spankbang.com", "beeg.com", "brazzers.com",
                "xhamster.com", "tube8.com", "txxx.com", "hclips.com"
            )

            for (kw in defaultKeywords) {
                repository.addBlocklistItem("blacklist", "keywords", kw)
            }
            for (site in defaultWebsites) {
                repository.addBlocklistItem("blacklist", "websites", site)
            }
            Timber.d("GuardianApp: seeded ${defaultKeywords.size} keywords, ${defaultWebsites.size} websites")
        } catch (e: Exception) {
            Timber.e(e, "GuardianApp: failed to seed default blocklists")
        }
    }

    private suspend fun seedCategoryDefaults() {
        try {
            val total = CategoryRegistry.totalPackages()
            Timber.d("GuardianApp: category registry covers $total packages across ${CategoryRegistry.all().size} categories")
        } catch (e: Exception) {
            Timber.e(e, "GuardianApp: failed to seed category defaults")
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.apply(base))
    }
}
