package com.xteink.companion.monetization

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.xteink.companion.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DistributionMonetizationGateway : MonetizationGateway, PurchasesUpdatedListener {
    private val mutableState = MutableStateFlow(
        MonetizationRuntimeState(
            distribution = DistributionChannel.Play,
            entitlement = if (BuildConfig.DEBUG) {
                EntitlementState.AdSupported
            } else {
                EntitlementState.Unknown
            },
            consent = ConsentState.Unknown,
            purchasePhase = PurchasePhase.Connecting,
            testMode = BuildConfig.DEBUG,
        ),
    )
    override val state: StateFlow<MonetizationRuntimeState> = mutableState

    private var productDetails: ProductDetails? = null
    private var consentInformation: ConsentInformation? = null
    private var adsInitialized = false
    private var started = false
    private var billingClient: BillingClient? = null

    override fun start(activity: Activity) {
        if (started) return
        started = true
        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        refreshConsent(activity)
        billingClient = BillingClient.newBuilder(activity.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .enableAutoServiceReconnection()
            .build()
            .also { client ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            queryProductAndPurchases()
                        } else {
                            fail("Google Play Billing unavailable: ${result.debugMessage}")
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        mutableState.value = mutableState.value.copy(
                            purchasePhase = PurchasePhase.Connecting,
                            message = "Reconnecting to Google Play…",
                        )
                    }
                })
            }
    }

    override fun buy(activity: Activity) {
        val client = billingClient ?: return fail("Google Play Billing is not ready")
        val details = productDetails ?: return fail("Ad-free product is not available")
        mutableState.value = mutableState.value.copy(
            purchasePhase = PurchasePhase.Purchasing,
            message = null,
        )
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            fail(result.debugMessage.ifBlank { "Google Play could not start the purchase" })
        }
    }

    override fun restore() {
        val client = billingClient
        if (client == null || !client.isReady) {
            fail("Google Play Billing is reconnecting")
            return
        }
        mutableState.value = mutableState.value.copy(
            purchasePhase = PurchasePhase.Connecting,
            message = "Checking your Play purchases…",
        )
        queryPurchases(client)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> mutableState.value = mutableState.value.copy(
                purchasePhase = if (productDetails == null) PurchasePhase.Unavailable else PurchasePhase.Ready,
                message = "Purchase canceled",
            )
            else -> fail(result.debugMessage.ifBlank { "Google Play did not complete the purchase" })
        }
    }

    override fun refreshConsent(activity: Activity) {
        val info = consentInformation ?: UserMessagingPlatform.getConsentInformation(activity).also {
            consentInformation = it
        }
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateConsentState(activity)
                }
                updateConsentState(activity)
            },
            { error ->
                mutableState.value = mutableState.value.copy(
                    consent = ConsentState.AdsNotAllowed,
                    message = "Ad privacy check unavailable: ${error.message}",
                )
            },
        )
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                mutableState.value = mutableState.value.copy(message = error.message)
            }
            updateConsentState(activity)
        }
    }

    override fun close() {
        billingClient?.endConnection()
        billingClient = null
        started = false
    }

    private fun queryProductAndPurchases() {
        val client = billingClient ?: return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BuildConfig.PLAY_AD_FREE_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
        ) { result, detailsResult ->
            productDetails = detailsResult.productDetailsList.firstOrNull()
            val details = productDetails
            val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
            mutableState.value = mutableState.value.copy(
                purchasePhase = if (details == null) PurchasePhase.Unavailable else PurchasePhase.Ready,
                product = if (details == null || offer == null) null else StoreProductUi(
                    productId = details.productId,
                    title = details.name,
                    formattedPrice = offer.formattedPrice,
                ),
                message = if (result.responseCode == BillingClient.BillingResponseCode.OK && details == null) {
                    "Ad-free purchase is not configured for this Play build"
                } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    result.debugMessage
                } else {
                    null
                },
            )
            queryPurchases(client)
        }
    }

    private fun queryPurchases(client: BillingClient) {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            } else {
                fail(result.debugMessage.ifBlank { "Play purchases could not be restored" })
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val matchingPurchases = purchases.filter {
            BuildConfig.PLAY_AD_FREE_PRODUCT_ID in it.products
        }
        val pendingPurchase = matchingPurchases.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PENDING
        }
        if (pendingPurchase != null) {
            mutableState.value = mutableState.value.copy(
                purchasePhase = PurchasePhase.Pending,
                message = "Purchase pending in Google Play",
            )
            return
        }
        val purchase = matchingPurchases.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (purchase == null) {
            mutableState.value = mutableState.value.copy(
                entitlement = if (BuildConfig.DEBUG) {
                    EntitlementState.AdSupported
                } else {
                    EntitlementState.Unknown
                },
                purchasePhase = if (productDetails == null) PurchasePhase.Unavailable else PurchasePhase.Ready,
                message = null,
            )
            return
        }
        if (!BuildConfig.DEBUG) {
            mutableState.value = mutableState.value.copy(
                entitlement = EntitlementState.Unknown,
                purchasePhase = PurchasePhase.Verifying,
                message = "Purchase received · waiting for server verification",
            )
        } else if (!purchase.isAcknowledged) {
            billingClient?.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) finishDebugPurchase()
                else fail(result.debugMessage.ifBlank { "Play purchase acknowledgement failed" })
            }
        } else {
            finishDebugPurchase()
        }
    }

    private fun finishDebugPurchase() {
        mutableState.value = mutableState.value.copy(
            entitlement = EntitlementState.Purchased,
            purchasePhase = PurchasePhase.Purchased,
            message = "Test purchase restored · ads removed",
        )
    }

    private fun updateConsentState(activity: Activity) {
        val info = consentInformation ?: return
        val canRequest = info.canRequestAds()
        mutableState.value = mutableState.value.copy(
            consent = if (canRequest) ConsentState.AdsAllowed else ConsentState.AdsNotAllowed,
            privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
        )
        if (canRequest && !adsInitialized) {
            adsInitialized = true
            MobileAds.initialize(activity.applicationContext)
        }
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(
            purchasePhase = PurchasePhase.Failed,
            message = message,
        )
    }
}
