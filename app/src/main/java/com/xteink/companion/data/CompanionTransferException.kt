package com.xteink.companion.data

/** The X3 understood a command but refused to apply it. Retrying unchanged input cannot help. */
class CompanionCommandRejectedException(message: String) : IllegalStateException(message)

/** The transport disappeared or timed out. Durable user intent may resume on a fresh link. */
class CompanionTransportInterruptedException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
