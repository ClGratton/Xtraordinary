package com.xteink.companion.monetization

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

enum class PremiumAction {
    FocusOnDevice,
    SendPass,
    PushBooks,
}

data class AccessState(
    val isPlayDistribution: Boolean,
    val isPro: Boolean,
    val welcomeEndsAtEpochMs: Long?,
    val rewardedAccessEndsAtEpochMs: Long?,
    val localizedProPrice: String?,
    val billingAvailable: Boolean,
    val rewardedAdReady: Boolean,
    val privacyOptionsRequired: Boolean = false,
    val message: String? = null,
) {
    fun hasConnectedAccess(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        !isPlayDistribution ||
            isPro ||
            (welcomeEndsAtEpochMs?.let { nowEpochMs < it } == true) ||
            (rewardedAccessEndsAtEpochMs?.let { nowEpochMs < it } == true)

    fun welcomeDaysRemaining(nowEpochMs: Long = System.currentTimeMillis()): Int? {
        val end = welcomeEndsAtEpochMs ?: return null
        if (nowEpochMs >= end) return 0
        return ((end - nowEpochMs + DayMs - 1) / DayMs).toInt()
    }

    companion object {
        const val DayMs = 24L * 60L * 60L * 1_000L
    }
}

interface AccessController : AutoCloseable {
    val state: StateFlow<AccessState>

    fun start(activity: Activity)

    fun refresh()

    fun showRewardedAd(activity: Activity, onGranted: () -> Unit)

    fun launchProPurchase(activity: Activity)

    fun showPrivacyOptions(activity: Activity)
}
