package com.xteink.companion.data

/** Identifies one caller that currently needs a responsive, two-way device link. */
@JvmInline
internal value class InteractiveTransportOwner(val key: String)

/**
 * Feature-agnostic ownership and replay policy for a low-latency BLE lease.
 *
 * The first owner starts the shared session and the last owner releases it.
 * A newly observed capabilities sequence represents a new protocol session, so
 * the lease is replayed once. Callers may force a replay at a transaction
 * boundary without creating feature-specific transport state.
 */
internal class InteractiveTransportCoordinator {
    private val owners = linkedSetOf<InteractiveTransportOwner>()
    private var appliedCapabilitiesSequence = UnappliedSequence

    val isActive: Boolean
        get() = owners.isNotEmpty()

    fun acquire(owner: InteractiveTransportOwner): Boolean {
        val wasInactive = owners.isEmpty()
        owners += owner
        return wasInactive && owners.isNotEmpty()
    }

    fun release(owner: InteractiveTransportOwner): Boolean {
        val removed = owners.remove(owner)
        return removed && owners.isEmpty()
    }

    fun shouldApplyLease(capabilitiesSequence: Long, force: Boolean = false): Boolean {
        if (!isActive) return false
        if (!force && appliedCapabilitiesSequence == capabilitiesSequence) return false
        appliedCapabilitiesSequence = capabilitiesSequence
        return true
    }

    fun markLeaseFailed(capabilitiesSequence: Long) {
        if (appliedCapabilitiesSequence == capabilitiesSequence) {
            appliedCapabilitiesSequence = UnappliedSequence
        }
    }

    fun clear() {
        owners.clear()
        appliedCapabilitiesSequence = UnappliedSequence
    }

    private companion object {
        const val UnappliedSequence = -1L
    }
}
