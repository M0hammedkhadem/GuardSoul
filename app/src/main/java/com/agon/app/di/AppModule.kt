package com.agon.app.di

import com.agon.app.data.local.AppDatabase
import com.agon.app.data.local.dao.KeywordDao
import com.agon.app.data.repository.AppRepository
import com.agon.app.data.settings.AppSettings
import com.agon.app.data.settings.EncryptedPrefs
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val databaseModule = module {
    single { AppDatabase.getInstance(androidApplication()) }
    single { get<AppDatabase>().blockEventDao() }
    single { get<AppDatabase>().keywordDao() }
}

val settingsModule = module {
    single { AppSettings(androidApplication()) }
    single { EncryptedPrefs(androidApplication()) }
}

val repositoryModule = module {
    single {
        AppRepository(
            blockEventDao = get(),
            keywordDao = get(),
            settings = get(),
            encryptedPrefs = get()
        )
    }
}

val appModules = listOf(
    databaseModule,
    settingsModule,
    repositoryModule
)
