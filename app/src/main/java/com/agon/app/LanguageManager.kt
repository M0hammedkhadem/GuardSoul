package com.agon.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageManager {
    var currentLanguageCode: String = "en"

    fun apply(context: Context): Context {
        val prefs = context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
        currentLanguageCode = prefs.getString("lang", "en") ?: "en"
        val locale = Locale(currentLanguageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun setLanguage(context: Context, lang: String) {
        currentLanguageCode = lang
        context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
    }
}
