package com.xteink.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalFirmwareInspectionTest {
    @Test
    fun readsEmbeddedXtraordinaryVersion() {
        val image = byteArrayOf(0xE9.toByte(), 0) +
            "prefix xtraordinary-v0.2.6-dev16-local\u0000suffix".toByteArray(Charsets.ISO_8859_1)

        assertEquals(
            "xtraordinary-v0.2.6-dev16-local",
            inspectLocalX3Firmware(image),
        )
    }

    @Test
    fun rejectsAnEspImageWithoutXtraordinaryIdentity() {
        val image = byteArrayOf(0xE9.toByte(), 0, 1, 2, 3)

        assertThrows(IllegalStateException::class.java) {
            inspectLocalX3Firmware(image)
        }
    }

    @Test
    fun rejectsANonEspApplicationImage() {
        assertThrows(IllegalArgumentException::class.java) {
            inspectLocalX3Firmware("not firmware".toByteArray())
        }
    }
}
