package com.xteink.companion.ui

internal data class PersistedTicketModes(val selected: TicketMode, val deployed: TicketMode?)

internal fun decodeTicketModes(
    legacyMode: String?,
    selectedMode: String?,
    deployedMode: String?,
    deployedPresent: Boolean,
): PersistedTicketModes {
    fun decode(value: String?) = value?.let { runCatching { TicketMode.valueOf(it) }.getOrNull() }
    val legacy = decode(legacyMode) ?: TicketMode.Static
    return PersistedTicketModes(
        selected = decode(selectedMode) ?: legacy,
        deployed = if (deployedPresent) decode(deployedMode) ?: legacy else null,
    )
}
