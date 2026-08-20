package com.xteink.companion.monetization

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.xteink.companion.BuildConfig

@Composable
fun DistributionBanner(
    runtime: MonetizationRuntimeState,
    surface: MonetizationSurface,
    operationInProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    val decision = MonetizationPolicy.bannerDecision(
        MonetizationContext(
            distribution = runtime.distribution,
            entitlement = runtime.entitlement,
            consent = runtime.consent,
            surface = surface,
            operationInProgress = operationInProgress,
        ),
    )
    if (decision != BannerDecision.RequestAnchoredAdaptiveBanner) return

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(320)
    val adView = remember(BuildConfig.ADMOB_BANNER_UNIT_ID, widthDp) {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose(adView::destroy) }
    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth(),
    )
}
