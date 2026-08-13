package com.xteink.companion.monetization

enum class DistributionChannel { Community, Play }

sealed interface EntitlementState {
    data object Unknown : EntitlementState
    data class UnknownOffline(val graceUntilEpochMs: Long) : EntitlementState
    data class TrialActive(val endsAtEpochMs: Long) : EntitlementState
    data object AdSupported : EntitlementState
    data object Purchased : EntitlementState
}

enum class ConsentState { Unknown, AdsAllowed, AdsNotAllowed }

enum class MonetizationSurface {
    Focus,
    ReadLibrary,
    ToolsHub,
    Passes,
    ReadingStats,
    Setup,
    Pairing,
    Transfer,
    Firmware,
    GoogleAuthorization,
    DestructiveConfirmation,
    ErrorRecovery,
}

data class MonetizationContext(
    val distribution: DistributionChannel,
    val entitlement: EntitlementState,
    val consent: ConsentState,
    val surface: MonetizationSurface,
    val operationInProgress: Boolean = false,
)

sealed interface BannerDecision {
    data object RequestAnchoredAdaptiveBanner : BannerDecision
    data class Hidden(val reason: String) : BannerDecision
}

object MonetizationPolicy {
    private val stableBrowsingSurfaces = setOf(
        MonetizationSurface.ReadLibrary,
        MonetizationSurface.ToolsHub,
        MonetizationSurface.Passes,
        MonetizationSurface.ReadingStats,
    )

    fun bannerDecision(context: MonetizationContext): BannerDecision = when {
        context.distribution == DistributionChannel.Community -> BannerDecision.Hidden("Community builds are ad-free")
        context.entitlement != EntitlementState.AdSupported -> BannerDecision.Hidden("Entitlement does not permit ads")
        context.consent != ConsentState.AdsAllowed -> BannerDecision.Hidden("Consent policy has not allowed an ad request")
        context.operationInProgress -> BannerDecision.Hidden("Hardware operation is active")
        context.surface !in stableBrowsingSurfaces -> BannerDecision.Hidden("Surface is not a stable browsing surface")
        else -> BannerDecision.RequestAnchoredAdaptiveBanner
    }
}

fun interface EntitlementClock {
    fun nowEpochMs(): Long
}

object SystemEntitlementClock : EntitlementClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}

data class ServerEntitlementSnapshot(
    val trialEndsAtEpochMs: Long?,
    val lifetimePurchaseActive: Boolean,
    val verifiedAtEpochMs: Long,
)

object EntitlementReducer {
    const val OfflineGraceMs = 24 * 60 * 60_000L

    fun fromServer(snapshot: ServerEntitlementSnapshot, clock: EntitlementClock): EntitlementState {
        if (snapshot.lifetimePurchaseActive) return EntitlementState.Purchased
        val trialEnd = snapshot.trialEndsAtEpochMs
        return if (trialEnd != null && clock.nowEpochMs() < trialEnd) {
            EntitlementState.TrialActive(trialEnd)
        } else {
            EntitlementState.AdSupported
        }
    }

    fun whileOffline(lastVerified: ServerEntitlementSnapshot?, clock: EntitlementClock): EntitlementState {
        if (lastVerified == null) return EntitlementState.Unknown
        if (lastVerified.lifetimePurchaseActive) return EntitlementState.Purchased
        val now = clock.nowEpochMs()
        val graceUntil = lastVerified.verifiedAtEpochMs + OfflineGraceMs
        if (now > graceUntil) return EntitlementState.Unknown
        val trialEnd = lastVerified.trialEndsAtEpochMs
        return if (trialEnd != null && now < trialEnd) {
            EntitlementState.TrialActive(trialEnd)
        } else {
            EntitlementState.UnknownOffline(graceUntil)
        }
    }
}
