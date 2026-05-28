package com.agon.app

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.languageDataStore by preferencesDataStore(name = "language_settings")

object LanguageManager {
    @Volatile
    var currentLanguageCode: String = "en"
    private val KEY = stringPreferencesKey("app_language")

    fun languageFlow(context: Context): Flow<String> =
        context.languageDataStore.data.map { it[KEY] ?: "en" }

    suspend fun setLanguage(context: Context, code: String) {
        context.languageDataStore.edit { it[KEY] = code }
        currentLanguageCode = code
    }

    fun apply(context: Context): Context {
        val locale = Locale.forLanguageTag(currentLanguageCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
