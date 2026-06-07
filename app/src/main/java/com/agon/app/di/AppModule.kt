package com.agon.app.di

import com.agon.app.account.AuthRepository
import com.agon.app.account.CloudSyncRepository
import com.agon.app.analytics.AnalyticsManager
import com.agon.app.analytics.CrashReporter
import com.agon.app.analytics.InAppUpdater
import com.agon.app.analytics.ReviewPrompt
import com.agon.app.billing.BillingClientWrapper
import com.agon.app.billing.BillingManager
import com.agon.app.blocking.AiBlockTracker
import com.agon.app.consent.ConsentCache
import com.agon.app.consent.ConsentManager
import com.agon.app.data.local.AppDatabase
import com.agon.app.data.repository.AppRepository
import com.agon.app.data.settings.AppSettings
import com.agon.app.data.settings.EncryptedPrefs
import com.agon.app.utils.SmartDetectionEngine
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val databaseModule = module {
    single { AppDatabase.getInstance(androidApplication()) }

    single { get<AppDatabase>().blockEventDao() }
    single { get<AppDatabase>().blocklistDao() }
    single { get<AppDatabase>().appLimitDao() }
    single { get<AppDatabase>().scheduleRuleDao() }
    single { get<AppDatabase>().tamperAlertDao() }
}

val settingsModule = module {
    single { AppSettings(androidApplication()) }
    single { EncryptedPrefs(androidApplication()) }
    single { AiBlockTracker(get()) }
}

val repositoryModule = module {
    single {
        AppRepository(
            application = androidApplication(),
            blockEventDao = get(),
            blocklistDao = get(),
            appLimitDao = get(),
            scheduleRuleDao = get(),
            tamperAlertDao = get(),
            settings = get(),
            encryptedPrefs = get()
        )
    }
}

val utilsModule = module {
    single { SmartDetectionEngine() }
}

/**
 * Analytics + crash reporting.
 *
 * The previous version used `runBlocking { consentFlow.first() }`
 * inside the lambda to read the current consent flag every time
 * the guard fired. That's fine in the background, but
 * `AnalyticsManager` / `CrashReporter` are also consulted from
 * synchronous Firebase callbacks (e.g. FirebaseAnalytics.logEvent
 * runs on the calling thread, and many call sites are the UI
 * thread). The `runBlocking` was occasionally triggering
 * `IllegalStateException: Cannot invoke blocking operation on
 * default dispatcher` on the main thread.
 *
 * The fix: cache the consent flags in a [ConsentCache] singleton
 * that is refreshed by a coroutine launched from `GuardianApp`
 * at app start. The analytics / crash guards become a
 * non-blocking property read.
 */
val consentModule = module {
    single { ConsentCache() }
}

val analyticsModule = module {
    single {
        AnalyticsManager(androidApplication()) {
            get<ConsentCache>().analyticsConsent()
        }
    }
    single {
        CrashReporter(androidApplication()) {
            get<ConsentCache>().crashConsent()
        }
    }
    single { ReviewPrompt(androidApplication(), get()) }
    single { InAppUpdater(androidApplication()) }
}

val billingModule = module {
    single { BillingClientWrapper(androidApplication()) }
    single {
        BillingManager(
            context = androidApplication(),
            settings = get(),
            wrapper = get()
        )
    }
}

val accountModule = module {
    single { AuthRepository(androidApplication(), get()) }
    single { CloudSyncRepository(get(), get(), get()) }
    single {
        ConsentManager(
            context = androidApplication(),
            settings = get(),
            analytics = get(),
            crash = get()
        )
    }
}

val appModules = listOf(
    databaseModule,
    settingsModule,
    repositoryModule,
    utilsModule,
    consentModule,
    analyticsModule,
    billingModule,
    accountModule
)
