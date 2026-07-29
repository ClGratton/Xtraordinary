package com.xteink.companion.monetization

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AccessControllerFactory {
    fun create(context: Context): AccessController = CommunityAccessController
}

private object CommunityAccessController : AccessController {
    private val unlocked = MutableStateFlow(
        AccessState(
            isPlayDistribution = false,
            isPro = true,
            welcomeEndsAtEpochMs = null,
            rewardedAccessEndsAtEpochMs = null,
            localizedProPrice = null,
            billingAvailable = false,
            rewardedAdReady = false,
        ),
    )

    override val state: StateFlow<AccessState> = unlocked

    override fun start(activity: Activity) = Unit

    override fun refresh() = Unit

    override fun showRewardedAd(activity: Activity, onGranted: () -> Unit) = onGranted()

    override fun launchProPurchase(activity: Activity) = Unit

    override fun showPrivacyOptions(activity: Activity) = Unit

    override fun close() = Unit
}
