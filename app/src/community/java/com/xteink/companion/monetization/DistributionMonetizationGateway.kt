package com.xteink.companion.monetization

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DistributionMonetizationGateway : MonetizationGateway {
    private val mutableState = MutableStateFlow(
        MonetizationRuntimeState(
            distribution = DistributionChannel.Community,
            entitlement = EntitlementState.Purchased,
            consent = ConsentState.AdsNotAllowed,
            purchasePhase = PurchasePhase.Unavailable,
            message = "Community build · ad-free",
        ),
    )
    override val state: StateFlow<MonetizationRuntimeState> = mutableState

    override fun start(activity: Activity) = Unit
    override fun buy(activity: Activity) = Unit
    override fun restore() = Unit
    override fun refreshConsent(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
    override fun close() = Unit
}
