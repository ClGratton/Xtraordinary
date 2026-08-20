package com.xteink.companion.monetization

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

data class StoreProductUi(
    val productId: String,
    val title: String,
    val formattedPrice: String,
)

enum class PurchasePhase {
    Unavailable,
    Connecting,
    Ready,
    Purchasing,
    Pending,
    Verifying,
    Purchased,
    Failed,
}

data class MonetizationRuntimeState(
    val distribution: DistributionChannel,
    val entitlement: EntitlementState,
    val consent: ConsentState,
    val purchasePhase: PurchasePhase,
    val product: StoreProductUi? = null,
    val message: String? = null,
    val privacyOptionsRequired: Boolean = false,
    val testMode: Boolean = false,
) {
    val isCommunity: Boolean get() = distribution == DistributionChannel.Community
    val isPurchased: Boolean get() = entitlement == EntitlementState.Purchased
}

interface MonetizationGateway {
    val state: StateFlow<MonetizationRuntimeState>

    fun start(activity: Activity)
    fun buy(activity: Activity)
    fun restore()
    fun refreshConsent(activity: Activity)
    fun showPrivacyOptions(activity: Activity)
    fun close()
}
