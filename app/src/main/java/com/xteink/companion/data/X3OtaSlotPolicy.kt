package com.xteink.companion.data

internal data class X3OtaSelection(
    val runningLabel: String,
    val runningOffset: Int,
    val bootLabel: String,
    val bootOffset: Int,
) {
    fun requireSelectedFlashOffset(): Int {
        require(runningLabel == bootLabel && runningOffset == bootOffset) {
            "X3 running and next-boot OTA slots differ; firmware flashing is blocked"
        }
        val expectedOffset = when (bootLabel) {
            "app0" -> EspRomProtocol.App0Offset
            "app1" -> EspRomProtocol.App1Offset
            else -> error("Unsupported X3 boot partition $bootLabel")
        }
        require(bootOffset == expectedOffset) {
            "X3 boot partition $bootLabel has unexpected offset 0x${bootOffset.toString(16)}"
        }
        return expectedOffset
    }
}

internal object X3OtaSlotPolicy {
    private val otaTrace = Regex(
        """^RUNTIME_TRACE_OTA running_label=(\S+) running_offset=(0x[0-9A-Fa-f]+) boot_label=(\S+) boot_offset=(0x[0-9A-Fa-f]+)\r?$""",
        RegexOption.MULTILINE,
    )

    fun parseRuntimeTrace(trace: String): X3OtaSelection {
        val match = otaTrace.find(trace)
            ?: error("X3 firmware does not report its running OTA slot; use the guarded Windows flasher to bootstrap")
        return X3OtaSelection(
            runningLabel = match.groupValues[1],
            runningOffset = parseOffset(match.groupValues[2]),
            bootLabel = match.groupValues[3],
            bootOffset = parseOffset(match.groupValues[4]),
        )
    }

    private fun parseOffset(value: String): Int =
        value.removePrefix("0x").removePrefix("0X").toLong(16).also {
            require(it in 0..Int.MAX_VALUE) { "X3 OTA offset is outside supported flash" }
        }.toInt()
}
