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
import com.agon.app.consent.ConsentManager
import com.agon.app.data.local.AppDatabase
import com.agon.app.data.repository.AppRepository
import com.agon.app.data.settings.AppSettings
import com.agon.app.data.settings.EncryptedPrefs
import com.agon.app.utils.SmartDetectionEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
            settings = get()
        )
    }
}

val utilsModule = module {
    single { SmartDetectionEngine() }
}

/**
 * Analytics + crash reporting. The consent provider reads the current
 * DataStore value lazily, so we don't need to wire the consent manager
 * here (it would create a cycle).
 */
val analyticsModule = module {
    single {
        AnalyticsManager(androidApplication()) {
            runCatching {
                runBlocking { get<AppSettings>().consentAnalyticsFlow.first() }
            }.getOrDefault(false)
        }
    }
    single {
        CrashReporter(androidApplication()) {
            runCatching {
                runBlocking { get<AppSettings>().consentCrashFlow.first() }
            }.getOrDefault(false)
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
    analyticsModule,
    billingModule,
    accountModule
)
