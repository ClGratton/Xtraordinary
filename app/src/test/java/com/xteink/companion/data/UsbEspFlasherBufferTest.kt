package com.xteink.companion.data

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals

class UsbEspFlasherBufferTest {
    @Test
    fun combinedRomAckAndStubBannerKeepsBannerForSerialReader() {
        val pending = ArrayDeque<Int>()
        "OHAI\n".toByteArray().forEach { pending.addLast(it.toInt() and 0xff) }
        val output = ByteArrayOutputStream()
        consumePendingUsbText(pending, output)
        assertEquals("OHAI\n", output.toString(Charsets.UTF_8.name()))
        assertEquals(0, pending.size)
    }

    @Test
    fun splitStubBannerIsConsumedAcrossBufferedReads() {
        val pending = ArrayDeque<Int>()
        "OH".toByteArray().forEach { pending.addLast(it.toInt() and 0xff) }
        val output = ByteArrayOutputStream()
        consumePendingUsbText(pending, output)
        "AI\n".toByteArray().forEach { pending.addLast(it.toInt() and 0xff) }
        consumePendingUsbText(pending, output)
        assertEquals("OHAI\n", output.toString(Charsets.UTF_8.name()))
    }
}
