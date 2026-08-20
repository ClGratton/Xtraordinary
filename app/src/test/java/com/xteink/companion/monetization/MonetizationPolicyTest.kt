package com.xteink.companion.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetizationPolicyTest {
    @Test
    fun communityDistributionNeverRequestsAds() {
        val decision = MonetizationPolicy.bannerDecision(
            MonetizationContext(
                distribution = DistributionChannel.Community,
                entitlement = EntitlementState.AdSupported,
                consent = ConsentState.AdsAllowed,
                surface = MonetizationSurface.ReadLibrary,
            ),
        )
        assertTrue(decision is BannerDecision.Hidden)
    }

    @Test
    fun playBannerNeedsExpiredEntitlementConsentAndStableSurface() {
        val allowed = MonetizationContext(
            distribution = DistributionChannel.Play,
            entitlement = EntitlementState.AdSupported,
            consent = ConsentState.AdsAllowed,
            surface = MonetizationSurface.Passes,
        )
        assertEquals(BannerDecision.RequestAnchoredAdaptiveBanner, MonetizationPolicy.bannerDecision(allowed))
        assertTrue(
            MonetizationPolicy.bannerDecision(allowed.copy(surface = MonetizationSurface.Transfer)) is BannerDecision.Hidden,
        )
        assertTrue(
            MonetizationPolicy.bannerDecision(allowed.copy(operationInProgress = true)) is BannerDecision.Hidden,
        )
        MonetizationSurface.entries
            .filterNot { it in setOf(
                MonetizationSurface.ReadLibrary,
                MonetizationSurface.ToolsHub,
                MonetizationSurface.Passes,
                MonetizationSurface.ReadingStats,
            ) }
            .forEach { surface ->
                assertTrue(
                    "$surface must never request an ad",
                    MonetizationPolicy.bannerDecision(allowed.copy(surface = surface)) is BannerDecision.Hidden,
                )
            }
        listOf(
            EntitlementState.Unknown,
            EntitlementState.UnknownOffline(2_000L),
            EntitlementState.TrialActive(2_000L),
            EntitlementState.Purchased,
        ).forEach { entitlement ->
            assertTrue(
                "$entitlement must never request an ad",
                MonetizationPolicy.bannerDecision(allowed.copy(entitlement = entitlement)) is BannerDecision.Hidden,
            )
        }
        assertTrue(
            MonetizationPolicy.bannerDecision(allowed.copy(consent = ConsentState.AdsNotAllowed)) is BannerDecision.Hidden,
        )
        assertTrue(
            MonetizationPolicy.bannerDecision(allowed.copy(consent = ConsentState.Unknown)) is BannerDecision.Hidden,
        )
    }

    @Test
    fun serverClockDrivesTrialAndPurchaseTransitions() {
        val clock = EntitlementClock { 1_000L }
        assertEquals(
            EntitlementState.TrialActive(2_000L),
            EntitlementReducer.fromServer(ServerEntitlementSnapshot(2_000L, false, 900L), clock),
        )
        assertEquals(
            EntitlementState.AdSupported,
            EntitlementReducer.fromServer(ServerEntitlementSnapshot(500L, false, 900L), clock),
        )
        assertEquals(
            EntitlementState.Purchased,
            EntitlementReducer.fromServer(ServerEntitlementSnapshot(null, true, 900L), clock),
        )
    }
}
