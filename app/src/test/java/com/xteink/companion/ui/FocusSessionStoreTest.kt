package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusSessionStoreTest {
    @Test
    fun runningSessionUsesItsOriginalDeadlineAfterProcessDeath() {
        val restored = PersistedFocusSession(
            task = "Write",
            selectedMinutes = 25,
            remainingSeconds = 1_500,
            phase = FocusPhase.Running,
            deadlineEpochMs = 190_000L,
        ).restoreAt(nowEpochMs = 100_000L)

        assertEquals(FocusPhase.Running, restored.phase)
        assertEquals(90, restored.remainingSeconds)
    }

    @Test
    fun elapsedRunningSessionRestoresToReview() {
        val restored = PersistedFocusSession(
            task = "Write",
            selectedMinutes = 25,
            remainingSeconds = 1,
            phase = FocusPhase.Running,
            deadlineEpochMs = 99_000L,
        ).restoreAt(nowEpochMs = 100_000L)

        assertEquals(FocusPhase.Review, restored.phase)
        assertEquals(0, restored.remainingSeconds)
    }

    @Test
    fun pausedSessionDoesNotElapseWhileTheProcessIsDead() {
        val restored = PersistedFocusSession(
            task = "Write",
            selectedMinutes = 25,
            remainingSeconds = 734,
            phase = FocusPhase.Paused,
            deadlineEpochMs = 1L,
        ).restoreAt(nowEpochMs = 999_999L)

        assertEquals(FocusPhase.Paused, restored.phase)
        assertEquals(734, restored.remainingSeconds)
    }

    @Test
    fun runningSnapshotRecordsAnAbsoluteDeadline() {
        val persisted = FocusUiState(
            selectedMinutes = 25,
            remainingSeconds = 42,
            phase = FocusPhase.Running,
        ).persistedAt(nowEpochMs = 10_000L)

        assertEquals(52_000L, persisted.deadlineEpochMs)
    }

    @Test
    fun pendingStartDoesNotBeginPhoneDeadlineBeforeDeviceAck() {
        val persisted = FocusUiState(
            selectedMinutes = 25,
            remainingSeconds = 1_500,
            phase = FocusPhase.Setup,
            pendingAction = FocusPendingAction.Start,
        ).persistedAt(nowEpochMs = 10_000L)

        assertEquals(FocusPhase.Setup, persisted.phase)
        assertEquals(FocusPendingAction.Start, persisted.pendingAction)
        assertEquals(0L, persisted.deadlineEpochMs)
    }

    @Test
    fun acknowledgedFocusActionsCommitOnlyTheirAppliedPhase() {
        val setup = FocusUiState(selectedMinutes = 25, pendingAction = FocusPendingAction.Start)
        val running = setup.applyAcknowledged(FocusPendingAction.Start)
        assertEquals(FocusPhase.Running, running.phase)
        assertEquals(null, running.pendingAction)

        val paused = running.copy(pendingAction = FocusPendingAction.Pause)
            .applyAcknowledged(FocusPendingAction.Pause)
        assertEquals(FocusPhase.Paused, paused.phase)

        val resumed = paused.copy(pendingAction = FocusPendingAction.Resume)
            .applyAcknowledged(FocusPendingAction.Resume)
        assertEquals(FocusPhase.Running, resumed.phase)

        val stopped = resumed.copy(pendingAction = FocusPendingAction.Stop)
            .applyAcknowledged(FocusPendingAction.Stop)
        assertEquals(FocusPhase.Setup, stopped.phase)
        assertEquals(1_500, stopped.remainingSeconds)
    }
}
