package com.xteink.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class X3OtaSlotPolicyTest {
    @Test
    fun selectedApp1TraceTargetsApp1() {
        val trace = """
            RUNTIME_TRACE_OTA running_label=app1 running_offset=0x00650000 boot_label=app1 boot_offset=0x00650000
            RUNTIME_TRACE_ACTIVE boot=5 power_held_ms=0
        """.trimIndent()

        val selection = X3OtaSlotPolicy.parseRuntimeTrace(trace)

        assertEquals(EspRomProtocol.App1Offset, selection.requireSelectedFlashOffset())
    }

    @Test
    fun missingOtaEvidenceFailsClosed() {
        assertThrows(IllegalStateException::class.java) {
            X3OtaSlotPolicy.parseRuntimeTrace("RUNTIME_TRACE_ACTIVE boot=5 power_held_ms=0")
        }
    }

    @Test
    fun pendingBootSlotChangeFailsClosed() {
        val selection = X3OtaSelection(
            runningLabel = "app0",
            runningOffset = EspRomProtocol.App0Offset,
            bootLabel = "app1",
            bootOffset = EspRomProtocol.App1Offset,
        )

        assertThrows(IllegalArgumentException::class.java) {
            selection.requireSelectedFlashOffset()
        }
    }

    @Test
    fun unexpectedLabelOrOffsetFailsClosed() {
        val selection = X3OtaSelection(
            runningLabel = "factory",
            runningOffset = EspRomProtocol.App0Offset,
            bootLabel = "factory",
            bootOffset = EspRomProtocol.App0Offset,
        )

        assertThrows(IllegalStateException::class.java) {
            selection.requireSelectedFlashOffset()
        }
    }
}
