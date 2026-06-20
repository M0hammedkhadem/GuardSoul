package com.agon.app

import android.app.Activity
import android.app.Application
import android.content.Context
import com.agon.app.data.repository.AppRepository
import com.agon.app.di.appModules
import com.agon.app.logging.ReleaseTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

fun Context.guardianApp(): GuardianApp? {
    return applicationContext as? GuardianApp
}

class GuardianApp : Application() {
    val repository: AppRepository by inject()
    var currentActivity: Activity? = null
        private set

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setCurrentActivity(activity: Activity?) {
        currentActivity = activity
    }

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@GuardianApp)
            modules(appModules)
        }

        if (BuildConfig.IS_DEBUG_BUILD) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        // Warm the shield active cache so isShieldActiveSync() is safe to call
        // from the AccessibilityService without blocking the main thread.
        appScope.launch {
            try {
                repository.getAppSettings().warmShieldCache()
                Timber.d("GuardianApp: shield cache warmed")
            } catch (e: Exception) {
                Timber.w(e, "GuardianApp: failed to warm shield cache")
            }
        }

        // Seed default keywords and websites on first launch
        appScope.launch {
            try {
                seedDefaultBlocklists()
            } catch (e: Exception) {
                Timber.w(e, "GuardianApp: failed to seed defaults")
            }
        }
    }

    private suspend fun seedDefaultBlocklists() {
        val settings = repository.getAppSettings()
        val alreadySeeded = settings.isDefaultsSeeded()
        if (alreadySeeded) {
            Timber.d("GuardianApp: defaults already seeded, skipping")
            return
        }

        // Default keywords (blacklist)
        val defaultKeywords = setOf(
            "porn", "xxx", "sex", "nude", "naked", "hentai", "adult", "erotic", "nsfw", "fetish",
            "pornhub", "xvideos", "xnxx", "redtube", "youporn", "onlyfans", "fansly",
            "إباحي", "جنس", "عاري", "porno", "pornographie"
        )
        repository.addKeywords(defaultKeywords.toList(), isWhitelist = false)
        Timber.d("GuardianApp: seeded ${defaultKeywords.size} default keywords")

        // Default websites (blacklist)
        val defaultWebsites = setOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
            "xhamster.com", "spankbang.com", "chaturbate.com", "onlyfans.com",
            "brazzers.com", "naughtyamerica.com", "porn.com", "tube8.com",
            "xtube.com", "drtuber.com", "tnaflix.com", "sunporno.com",
            "pornhd.com", "porntrex.com", "eporner.com", "yourporn.com"
        )
        val currentSites = settings.blockedWebsitesFlow.first()
        settings.setBlockedWebsites(currentSites + defaultWebsites)
        Timber.d("GuardianApp: seeded ${defaultWebsites.size} default websites")

        // Mark as seeded
        settings.setDefaultsSeeded(true)
        Timber.i("GuardianApp: default blocklists seeded successfully")
    }
}
