package com.llmbalance.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_CHINESE = "zh"
    const val DEFAULT_LANGUAGE = LANGUAGE_CHINESE

    val supportedLanguages = listOf(
        LocaleDisplay(LANGUAGE_ENGLISH, "English"),
        LocaleDisplay(LANGUAGE_CHINESE, "中文")
    )

    fun wrapContext(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    fun getLocale(languageCode: String): Locale = Locale(languageCode)

    fun getDisplayName(languageCode: String): String {
        return supportedLanguages.find { it.code == languageCode }?.displayName ?: languageCode
    }
}

data class LocaleDisplay(
    val code: String,
    val displayName: String
)
