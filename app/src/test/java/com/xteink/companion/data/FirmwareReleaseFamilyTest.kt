package com.xteink.companion.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareReleaseFamilyTest {
    @Test fun companionLocalArtifactIsEligibleForManagedBleOnlyByEmbeddedFamily() {
        val release = FirmwareRelease("X3", FirmwareSource.LocalFile, "xtraordinary-v0.2.6-dev51", "x.bin", "file:/x", 1, "ab".repeat(32))
        assertTrue(isXtraordinaryCompanionFirmware(release))
    }

    @Test fun stockOrCrossPointNamesNeverBecomeCompanionOta() {
        val stock = FirmwareRelease("X3", FirmwareSource.LocalFile, "XT-V5.1.6", "x.bin", "file:/x", 1, "ab".repeat(32))
        assertFalse(isXtraordinaryCompanionFirmware(stock))
    }
}
