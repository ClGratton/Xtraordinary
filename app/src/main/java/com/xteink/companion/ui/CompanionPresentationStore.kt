package com.xteink.companion.ui

import android.content.Context

internal fun decodeCompanionVisualTheme(
    storedValue: String?,
    fallback: CompanionVisualTheme = CompanionVisualTheme.Expressive,
): CompanionVisualTheme = storedValue
    ?.let { value -> if (value == "Quiet") CompanionVisualTheme.Minimal else CompanionVisualTheme.entries.firstOrNull { it.name == value } }
    ?: fallback

internal fun decodeCompanionColorMode(
    storedValue: String?,
    legacyTheme: String?,
    fallback: CompanionColorMode = CompanionColorMode.Light,
): CompanionColorMode = storedValue
    ?.let { value -> CompanionColorMode.entries.firstOrNull { it.name == value } }
    ?: if (legacyTheme == "Quiet") CompanionColorMode.Dark else fallback

/** Owns durable, phone-only presentation choices independently of X3 state. */
internal class CompanionPresentationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun loadVisualTheme(): CompanionVisualTheme = decodeCompanionVisualTheme(
        preferences.getString(VisualStyleKey, null)
            ?: preferences.getString(LegacyVisualThemeKey, null),
    )

    fun saveVisualTheme(theme: CompanionVisualTheme) {
        preferences.edit().putString(VisualStyleKey, theme.name).apply()
    }

    fun loadColorMode(): CompanionColorMode = decodeCompanionColorMode(
        storedValue = preferences.getString(ColorModeKey, null),
        legacyTheme = preferences.getString(LegacyVisualThemeKey, null),
    )

    fun saveColorMode(mode: CompanionColorMode) {
        preferences.edit().putString(ColorModeKey, mode.name).apply()
    }

    private companion object {
        const val PreferencesName = "xtraordinary_presentation"
        const val VisualStyleKey = "visual_style"
        const val ColorModeKey = "color_mode"
        const val LegacyVisualThemeKey = "visual_theme"
    }
}
