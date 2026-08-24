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

    /** Parses the two-sector ESP-IDF otadata image without assuming an offset. */
    fun parseOtadata(bytes: ByteArray): X3OtaSelection {
        require(bytes.size == 0x2000) { "X3 otadata must contain two 0x1000-byte sectors" }
        val entries = (0..1).map { index ->
            val base = index * 0x1000
            val sequence = readU32(bytes, base)
            val state = readU32(bytes, base + 24)
            val storedCrc = readU32(bytes, base + 28)
            Triple(index, sequence, state).takeIf {
                sequence != 0u && sequence != UInt.MAX_VALUE &&
                    state != 3u && state != 4u && storedCrc == otaSequenceCrc(sequence)
            }
        }.filterNotNull().sortedByDescending { it.second }
        require(entries.isNotEmpty()) { "No CRC-valid bootable X3 otadata entry" }
        val sequence = entries.first().second
        val slot = ((sequence - 1u) % 2u).toInt()
        return if (slot == 0) {
            X3OtaSelection("app0", 0x10000, "app0", 0x10000)
        } else {
            X3OtaSelection("app1", 0x650000, "app1", 0x650000)
        }
    }

    private fun readU32(bytes: ByteArray, offset: Int): UInt =
        (bytes[offset].toUInt() and 0xffu) or
            ((bytes[offset + 1].toUInt() and 0xffu) shl 8) or
            ((bytes[offset + 2].toUInt() and 0xffu) shl 16) or
            ((bytes[offset + 3].toUInt() and 0xffu) shl 24)

    private fun otaSequenceCrc(sequence: UInt): UInt {
        var crc = 0u
        val polynomial = 0xEDB88320u
        repeat(4) { index ->
            crc = crc xor ((sequence shr (index * 8)) and 0xffu)
            repeat(8) {
                crc = if ((crc and 1u) != 0u) (crc shr 1) xor polynomial else crc shr 1
            }
        }
        return crc xor UInt.MAX_VALUE
    }

    private fun parseOffset(value: String): Int =
        value.removePrefix("0x").removePrefix("0X").toLong(16).also {
            require(it in 0..Int.MAX_VALUE) { "X3 OTA offset is outside supported flash" }
        }.toInt()
}
