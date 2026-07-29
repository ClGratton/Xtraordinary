package com.xteink.companion.monetization

import android.app.Activity
import android.content.Context
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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.xteink.companion.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

object AccessControllerFactory {
    fun create(context: Context): AccessController = PlayAccessController(context.applicationContext)
}

private class PlayAccessController(
    private val context: Context,
) : AccessController, PurchasesUpdatedListener {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val adsInitialized = AtomicBoolean(false)
    private var rewardedAd: RewardedAd? = null
    private var proProduct: ProductDetails? = null
    private var billingClient: BillingClient? = null

    private val firstSeenAt = preferences.getLong(FirstSeenKey, 0L).takeIf { it > 0L }
        ?: System.currentTimeMillis().also {
            preferences.edit().putLong(FirstSeenKey, it).apply()
        }

    private val mutableState = MutableStateFlow(
        AccessState(
            isPlayDistribution = true,
            isPro = preferences.getBoolean(ProCacheKey, false),
            welcomeEndsAtEpochMs = firstSeenAt + WelcomeDurationMs,
            rewardedAccessEndsAtEpochMs = preferences.getLong(RewardUntilKey, 0L).takeIf { it > 0L },
            localizedProPrice = null,
            billingAvailable = false,
            rewardedAdReady = false,
        ),
    )
    override val state: StateFlow<AccessState> = mutableState

    override fun start(activity: Activity) {
        requestConsent(activity)
        connectBilling()
        refresh()
    }

    override fun refresh() {
        mutableState.update {
            it.copy(
                isPro = preferences.getBoolean(ProCacheKey, false),
                rewardedAccessEndsAtEpochMs = preferences.getLong(RewardUntilKey, 0L).takeIf { value -> value > 0L },
            )
        }
        if (consentInformation.canRequestAds()) initializeAds()
        if (rewardedAd == null && adsInitialized.get()) loadRewardedAd()
        if (billingClient?.isReady == true) queryPurchases()
    }

    override fun showRewardedAd(activity: Activity, onGranted: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            mutableState.update { it.copy(message = "The ad is still loading. Try again in a moment.") }
            if (consentInformation.canRequestAds()) {
                initializeAds()
                loadRewardedAd()
            }
            return
        }
        rewardedAd = null
        mutableState.update { it.copy(rewardedAdReady = false, message = null) }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewardedAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                mutableState.update { it.copy(message = "The ad could not be shown. Please try again.") }
                loadRewardedAd()
            }
        }
        ad.show(activity) {
            val rewardUntil = System.currentTimeMillis() + RewardDurationMs
            preferences.edit().putLong(RewardUntilKey, rewardUntil).apply()
            mutableState.update {
                it.copy(
                    rewardedAccessEndsAtEpochMs = rewardUntil,
                    message = null,
                )
            }
            onGranted()
        }
    }

    override fun launchProPurchase(activity: Activity) {
        val client = billingClient
        val details = proProduct
        if (client?.isReady != true || details == null) {
            mutableState.update { it.copy(message = "Google Play is not ready yet. Try again in a moment.") }
            connectBilling()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                details.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()
                    ?.offerToken
                    ?.takeIf(String::isNotBlank)
                    ?.let(::setOfferToken)
            }
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableState.update { it.copy(message = billingMessage(result)) }
        }
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                mutableState.update { it.copy(message = "Privacy choices could not be opened.") }
            } else {
                mutableState.update {
                    it.copy(
                        privacyOptionsRequired =
                            consentInformation.privacyOptionsRequirementStatus ==
                                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
                    )
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases.orEmpty().forEach(::processPurchase)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            mutableState.update { it.copy(message = billingMessage(result)) }
        }
    }

    private fun requestConsent(activity: Activity) {
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) {
                        mutableState.update { it.copy(message = "Ad privacy choices could not be loaded.") }
                    }
                    mutableState.update {
                        it.copy(
                            privacyOptionsRequired =
                                consentInformation.privacyOptionsRequirementStatus ==
                                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
                        )
                    }
                    if (consentInformation.canRequestAds()) initializeAds()
                }
            },
            {
                if (consentInformation.canRequestAds()) initializeAds()
            },
        )
    }

    private fun initializeAds() {
        if (!adsInitialized.compareAndSet(false, true)) return
        MobileAds.initialize(context) {
            loadRewardedAd()
        }
    }

    private fun loadRewardedAd() {
        if (!consentInformation.canRequestAds() || rewardedAd != null) return
        RewardedAd.load(
            context,
            BuildConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    mutableState.update { it.copy(rewardedAdReady = true, message = null) }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    mutableState.update { it.copy(rewardedAdReady = false) }
                }
            },
        )
    }

    private fun connectBilling() {
        if (billingClient?.isReady == true) return
        billingClient?.endConnection()
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .build()
            .also { client ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            mutableState.update { it.copy(billingAvailable = true, message = null) }
                            queryProduct()
                            queryPurchases()
                        } else {
                            mutableState.update {
                                it.copy(billingAvailable = false, message = billingMessage(result))
                            }
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        mutableState.update { it.copy(billingAvailable = false) }
                    }
                })
            }
    }

    private fun queryProduct() {
        val client = billingClient ?: return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BuildConfig.PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build(),
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            proProduct = queryResult.productDetailsList.firstOrNull()
            val price = proProduct?.oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.formattedPrice
                ?: proProduct?.oneTimePurchaseOfferDetails?.formattedPrice
            mutableState.update { it.copy(localizedProPrice = price) }
        }
    }

    private fun queryPurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.filter {
                    BuildConfig.PRO_PRODUCT_ID in it.products &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (owned.isEmpty()) {
                    preferences.edit().putBoolean(ProCacheKey, false).apply()
                    mutableState.update { it.copy(isPro = false) }
                } else {
                    owned.forEach(::processPurchase)
                }
            }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED ||
            BuildConfig.PRO_PRODUCT_ID !in purchase.products
        ) {
            return
        }
        scope.launch {
            val backendConfigured = BuildConfig.ENTITLEMENT_BACKEND_URL.isNotBlank()
            val verified = if (backendConfigured) verifyWithBackend(purchase.purchaseToken) else true
            if (!verified) {
                mutableState.update {
                    it.copy(message = "Google Play confirmed the purchase, but verification is temporarily unavailable.")
                }
                return@launch
            }
            preferences.edit().putBoolean(ProCacheKey, true).apply()
            mutableState.update { it.copy(isPro = true, message = null) }
            if (!purchase.isAcknowledged && !backendConfigured) {
                billingClient?.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build(),
                ) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        mutableState.update { it.copy(message = billingMessage(result)) }
                    }
                }
            }
        }
    }

    private fun verifyWithBackend(purchaseToken: String): Boolean = runCatching {
        val endpoint = URL(BuildConfig.ENTITLEMENT_BACKEND_URL.trimEnd('/') + "/v1/google-play/verify")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val payload = JSONObject()
            .put("packageName", context.packageName)
            .put("productId", BuildConfig.PRO_PRODUCT_ID)
            .put("purchaseToken", purchaseToken)
            .toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) return@runCatching false
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(response).optBoolean("entitled", false)
    }.getOrDefault(false)

    private fun billingMessage(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play purchases are unavailable on this device."
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
            "Google Play could not be reached. Check the connection and try again."
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            "This Google Play account already owns Pro. Restoring it now."
        else -> "Google Play could not complete that request."
    }

    override fun close() {
        billingClient?.endConnection()
        billingClient = null
        scope.cancel()
    }

    private companion object {
        const val PreferencesName = "xtraordinary_access"
        const val FirstSeenKey = "first_seen_epoch_ms"
        const val RewardUntilKey = "reward_until_epoch_ms"
        const val ProCacheKey = "pro_verified"
        const val WelcomeDurationMs = 7L * AccessState.DayMs
        const val RewardDurationMs = AccessState.DayMs
    }
}
