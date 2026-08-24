package com.xteink.companion.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject

internal data class Esp32StubImage(
    val entry: Int,
    val textStart: Int,
    val text: ByteArray,
)

internal object Esp32C3StubAsset {
    fun load(context: Context): Esp32StubImage {
        val json = context.assets.open("esptool-stub/esp32c3-v2.json").bufferedReader().use { JSONObject(it.readText()) }
        return Esp32StubImage(
            entry = json.getInt("entry"),
            textStart = json.getInt("text_start"),
            text = Base64.decode(json.getString("text"), Base64.DEFAULT),
        )
    }
}
