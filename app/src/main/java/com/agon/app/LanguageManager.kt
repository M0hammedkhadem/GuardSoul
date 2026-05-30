package com.agon.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "language_settings"
    private const val KEY_LANG = "app_language"

    @Volatile
    var currentLanguageCode: String = "en"
        private set

    fun load(context: Context): String {
        currentLanguageCode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "en") ?: "en"
        return currentLanguageCode
    }

    fun setLanguage(context: Context, code: String) {
        currentLanguageCode = code
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, code)
            .apply()
    }

    fun apply(context: Context): Context {
        val lang = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "en") ?: "en"
        currentLanguageCode = lang
        
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}
