package io.github.jqssun.airplay.receiver

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Presentation only: never changes sender negotiation, audio tracks or receiver identity. */
object AppLanguages {
    val choices = listOf(
        "tr" to "Türkçe", "en" to "English", "de" to "Deutsch",
        "fr" to "Français", "es" to "Español", "it" to "Italiano",
        "pt-BR" to "Português (Brasil)", "ru" to "Русский", "ar" to "العربية",
        "hi" to "हिन्दी", "id" to "Bahasa Indonesia", "ja" to "日本語",
        "ko" to "한국어", "zh-CN" to "简体中文", "vi" to "Tiếng Việt", "th" to "ไทย",
    )
    fun isSupportedSelection(value: String) = value == "system" || choices.any { it.first == value }

    fun resolve(selection: String, system: List<Locale>): Locale {
        if (selection != "system")
            return Locale.forLanguageTag(choices.firstOrNull { it.first == selection }?.first ?: "en")
        for (locale in system) {
            val language = if (locale.language == "in") "id" else locale.language
            val match = choices.firstOrNull {
                val candidate = Locale.forLanguageTag(it.first).language
                (if (candidate == "in") "id" else candidate) == language
            }
            if (match != null) return Locale.forLanguageTag(match.first)
        }
        return Locale.ENGLISH
    }

    fun configuration(source: Configuration, selection: String): Configuration = Configuration(source).apply {
        val locale = resolve(selection, (0 until source.locales.size()).map { source.locales[it] })
        setLocale(locale)
        setLayoutDirection(locale)
    }

    fun context(base: Context): Context {
        val selection = base.getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE)
            .getString(UserExperiencePrefs.LANGUAGE, "system") ?: "system"
        return base.createConfigurationContext(configuration(base.resources.configuration, selection))
    }
}
