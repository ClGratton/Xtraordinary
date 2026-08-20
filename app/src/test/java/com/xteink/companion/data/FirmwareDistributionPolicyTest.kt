package com.xteink.companion.data

import org.junit.Assert.assertThrows
import org.junit.Test

class FirmwareDistributionPolicyTest {
    @Test
    fun publicBuildRejectsXteinkOemFirmwareWithoutPermission() {
        assertThrows(IllegalArgumentException::class.java) {
            requireFirmwareSourceDistributionAllowed(
                source = FirmwareSource.XteinkStock,
                xteinkOemFirmwareAllowed = false,
            )
        }
    }

    @Test
    fun maintenanceBuildMayUsePinnedXteinkRecovery() {
        requireFirmwareSourceDistributionAllowed(
            source = FirmwareSource.XteinkStock,
            xteinkOemFirmwareAllowed = true,
        )
    }

    @Test
    fun publicBuildKeepsOpenAndLocalSourcesAvailable() {
        FirmwareSource.entries
            .filterNot { it == FirmwareSource.XteinkStock }
            .forEach { source ->
                requireFirmwareSourceDistributionAllowed(
                    source = source,
                    xteinkOemFirmwareAllowed = false,
                )
            }
    }
}
