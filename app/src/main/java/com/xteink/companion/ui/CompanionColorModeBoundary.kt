package com.xteink.companion.ui

/** Shared hysteresis for the artwork selector, haptics, and content polarity. */
internal object CompanionColorModeBoundary {
    const val darkEnter = 0.62f
    const val lightEnter = 0.38f

    fun modeForDisplayedPosition(current: CompanionColorMode, position: Float): CompanionColorMode = when (current) {
        CompanionColorMode.Light -> if (position >= darkEnter) CompanionColorMode.Dark else CompanionColorMode.Light
        CompanionColorMode.Dark -> if (position <= lightEnter) CompanionColorMode.Light else CompanionColorMode.Dark
    }
}
