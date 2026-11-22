package com.retroplay.diagnostics

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Idioma escolhido em Interface → Idioma. Fica espelhado num SharedPreferences porque
 * attachBaseContext precisa dele de forma síncrona (o DataStore é assíncrono).
 */
object AppLocale {
    private const val PREFS = "ui_locale"
    private const val KEY = "language"
    const val SYSTEM = "system"

    fun stored(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    /** Grava; true quando mudou (a Activity deve ser recriada). */
    fun store(context: Context, tag: String): Boolean {
        if (stored(context) == tag) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        return true
    }

    fun wrap(base: Context): Context {
        val tag = stored(base)
        if (tag == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
}
