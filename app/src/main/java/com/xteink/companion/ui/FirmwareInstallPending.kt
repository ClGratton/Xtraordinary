package com.xteink.companion.ui

import com.xteink.companion.data.FirmwareSource

/** Identity persisted when the user requests firmware installation. */
data class PendingFirmwareInstall(
    val model: String,
    val version: String,
    val sizeBytes: Long,
    val sha256: String,
    val source: FirmwareSource = FirmwareSource.LocalFile,
    val assetName: String = "pending-firmware.bin",
    val downloadUrl: String = "",
    val attempt: Int = 0,
)

/** Pure lifecycle rules shared by persistence and the BLE reconnect drain. */
object FirmwareInstallPendingPolicy {
    fun isSame(left: PendingFirmwareInstall, right: PendingFirmwareInstall): Boolean =
        left.model.equals(right.model, ignoreCase = true) &&
            left.version == right.version &&
            left.sizeBytes == right.sizeBytes &&
            left.sha256.equals(right.sha256, ignoreCase = true)

    fun shouldReplay(
        pending: PendingFirmwareInstall?,
        protocolReady: Boolean,
        supportsFirmwareUpdate: Boolean,
    ): Boolean = pending != null && protocolReady && supportsFirmwareUpdate

    fun afterDisconnect(pending: PendingFirmwareInstall): PendingFirmwareInstall = pending

    fun afterLeaseAck(pending: PendingFirmwareInstall): PendingFirmwareInstall = pending

    fun afterNack(pending: PendingFirmwareInstall): PendingFirmwareInstall? =
        pending.copy(attempt = pending.attempt + 1).takeUnless { it.attempt >= MaxManagedUpdateAttempts }

    private const val MaxManagedUpdateAttempts = 2
}
