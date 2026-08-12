package com.xteink.companion.ui

import android.content.Context

internal fun decodeCompanionVisualTheme(
    storedValue: String?,
    fallback: CompanionVisualTheme = CompanionVisualTheme.Expressive,
): CompanionVisualTheme = storedValue
    ?.let { value -> CompanionVisualTheme.entries.firstOrNull { it.name == value } }
    ?: fallback

/** Owns durable, phone-only presentation choices independently of X3 state. */
internal class CompanionPresentationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun loadVisualTheme(): CompanionVisualTheme = decodeCompanionVisualTheme(
        preferences.getString(VisualThemeKey, null),
    )

    fun saveVisualTheme(theme: CompanionVisualTheme) {
        preferences.edit().putString(VisualThemeKey, theme.name).apply()
    }

    private companion object {
        const val PreferencesName = "xtraordinary_presentation"
        const val VisualThemeKey = "visual_theme"
    }
}
