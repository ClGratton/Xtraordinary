package com.xteink.companion.ui

/**
 * Keeps the fast-discovery window strictly before sleep, matching the X3
 * firmware contract. The available values are supplied by the caller so the
 * Settings choices can change without duplicating timing thresholds here.
 */
internal fun isFastWindowChoiceEnabled(fastWindowMinutes: Int, sleepAfterMinutes: Int): Boolean =
    fastWindowMinutes < sleepAfterMinutes

internal fun selectFastWindow(
    policy: RadioPolicyUiState,
    fastWindowMinutes: Int,
    sleepChoicesMinutes: List<Int>,
): RadioPolicyUiState {
    val compatibleSleep = if (isFastWindowChoiceEnabled(fastWindowMinutes, policy.sleepAfterMinutes)) {
        policy.sleepAfterMinutes
    } else {
        sleepChoicesMinutes.sorted().firstOrNull { isFastWindowChoiceEnabled(fastWindowMinutes, it) }
            ?: return policy
    }
    return policy.copy(fastWindowMinutes = fastWindowMinutes, sleepAfterMinutes = compatibleSleep)
}

internal fun selectSleepAfter(
    policy: RadioPolicyUiState,
    sleepAfterMinutes: Int,
    fastWindowChoicesMinutes: List<Int>,
): RadioPolicyUiState {
    val compatibleFastWindow = if (isFastWindowChoiceEnabled(policy.fastWindowMinutes, sleepAfterMinutes)) {
        policy.fastWindowMinutes
    } else {
        fastWindowChoicesMinutes.sortedDescending()
            .firstOrNull { isFastWindowChoiceEnabled(it, sleepAfterMinutes) }
            ?: return policy
    }
    return policy.copy(fastWindowMinutes = compatibleFastWindow, sleepAfterMinutes = sleepAfterMinutes)
}
