package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FirmwareVersionPolicyTest {
    @Test fun newerDevBuildIsAvailable() = assertEquals(
        FirmwareCheckPhase.Available,
        firmwareCheckPhaseFor(
            currentVersion = "xtraordinary-v0.2.6-dev50",
            releaseVersion = "xtraordinary-v0.2.6-dev51",
        ),
    )

    @Test fun olderStableCatalogDoesNotDowngradeNewerDevBuild() = assertEquals(
        FirmwareCheckPhase.UpToDate,
        firmwareCheckPhaseFor(
            currentVersion = "xtraordinary-v0.2.6-dev51-maintenance-focus-passes-local",
            releaseVersion = "xtraordinary-v0.2.4",
        ),
    )

    @Test fun stableReleaseIsNewerThanMatchingDevBuild() = assertEquals(
        FirmwareCheckPhase.Available,
        firmwareCheckPhaseFor(
            currentVersion = "xtraordinary-v0.2.6-dev51",
            releaseVersion = "xtraordinary-v0.2.6",
        ),
    )

    @Test fun sameVersionIsUpToDate() = assertEquals(
        FirmwareCheckPhase.UpToDate,
        firmwareCheckPhaseFor(
            currentVersion = "xtraordinary-v0.2.6-dev51",
            releaseVersion = "xtraordinary-v0.2.6-dev51",
        ),
    )

    @Test fun differentFirmwareFamiliesAreInstallCandidatesNotNumericDowngrades() = assertEquals(
        FirmwareCheckPhase.Available,
        firmwareCheckPhaseFor(
            currentVersion = "XT V5.1.6 EN",
            releaseVersion = "xtraordinary-v0.2.6",
        ),
    )
}
