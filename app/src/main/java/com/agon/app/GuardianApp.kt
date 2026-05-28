package com.agon.app

import android.app.Application
import android.content.Context
import com.agon.app.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class GuardianApp : Application() {
    val repository: AppRepository by lazy { AppRepository(this) }

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
        AppNotificationChannels.createAll(this)
        CoroutineScope(Dispatchers.IO).launch {
            val code = LanguageManager.languageFlow(this@GuardianApp).first()
            LanguageManager.currentLanguageCode = code
            seedDefaultBlocklists()
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

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.apply(base))
    }
}
