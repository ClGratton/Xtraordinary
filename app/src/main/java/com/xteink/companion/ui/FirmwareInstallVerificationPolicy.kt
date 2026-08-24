package com.xteink.companion.ui

/**
 * Keeps an applied firmware transaction nonterminal until fresh capabilities
 * prove that the device is running the exact requested version.
 */
internal fun firmwareVerificationPhaseAfterCapabilities(
    phase: FirmwareCheckPhase,
    pendingVersion: String?,
    capabilitiesVersion: String?,
): FirmwareCheckPhase {
    if (phase != FirmwareCheckPhase.Verifying) return phase
    return if (pendingVersion != null && capabilitiesVersion == pendingVersion) {
        FirmwareCheckPhase.Complete
    } else {
        FirmwareCheckPhase.Verifying
    }
}
