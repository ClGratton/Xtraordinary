package com.xteink.companion.ui

import android.content.Context

internal data class PersistedFocusSession(
    val task: String,
    val selectedMinutes: Int,
    val remainingSeconds: Int,
    val phase: FocusPhase,
    val deadlineEpochMs: Long,
    val pendingAction: FocusPendingAction? = null,
)

internal fun PersistedFocusSession.restoreAt(nowEpochMs: Long): FocusUiState {
    val boundedMinutes = selectedMinutes.coerceIn(5, 60)
    val boundedRemaining = remainingSeconds.coerceIn(0, boundedMinutes * 60)
    if (phase != FocusPhase.Running) {
        return FocusUiState(
            task = task.take(80),
            selectedMinutes = boundedMinutes,
            remainingSeconds = boundedRemaining,
            phase = phase,
            pendingAction = pendingAction,
        ).readyAfterCompletion()
    }
    val millisecondsLeft = (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)
    val secondsLeft = ((millisecondsLeft + 999L) / 1_000L)
        .coerceAtMost((boundedMinutes * 60).toLong())
        .toInt()
    val restored = FocusUiState(
        task = task.take(80),
        selectedMinutes = boundedMinutes,
        remainingSeconds = secondsLeft,
        phase = if (secondsLeft == 0) FocusPhase.Setup else FocusPhase.Running,
        pendingAction = pendingAction,
    )
    return if (secondsLeft == 0) {
        restored.copy(remainingSeconds = boundedMinutes * 60, pendingAction = null)
    } else restored
}

/** Phone completion is locally ready for a new session; X3 owns its separate Done screen. */
internal fun FocusUiState.readyAfterCompletion(): FocusUiState =
    if (phase == FocusPhase.Review || (phase == FocusPhase.Running && remainingSeconds <= 0)) {
        copy(phase = FocusPhase.Setup, remainingSeconds = selectedMinutes.coerceIn(5, 60) * 60, pendingAction = null)
    } else this

internal fun FocusUiState.withSelectedDuration(minutes: Int): FocusUiState {
    val bounded = minutes.coerceIn(5, 60)
    return if (phase == FocusPhase.Setup) copy(selectedMinutes = bounded, remainingSeconds = bounded * 60) else this
}

internal fun FocusUiState.persistedAt(nowEpochMs: Long): PersistedFocusSession = PersistedFocusSession(
    task = task.take(80),
    selectedMinutes = selectedMinutes.coerceIn(5, 60),
    remainingSeconds = remainingSeconds.coerceIn(0, selectedMinutes.coerceIn(5, 60) * 60),
    phase = phase,
    deadlineEpochMs = if (phase == FocusPhase.Running) {
        nowEpochMs + remainingSeconds.coerceAtLeast(0) * 1_000L
    } else {
        0L
    },
    pendingAction = pendingAction,
)

internal fun FocusUiState.applyAcknowledged(action: FocusPendingAction): FocusUiState = when (action) {
    FocusPendingAction.Start -> copy(
        phase = FocusPhase.Running,
        remainingSeconds = selectedMinutes * 60,
        pendingAction = null,
    )
    FocusPendingAction.Pause -> copy(phase = FocusPhase.Paused, pendingAction = null)
    FocusPendingAction.Resume -> copy(phase = FocusPhase.Running, pendingAction = null)
    FocusPendingAction.Stop -> copy(
        phase = FocusPhase.Setup,
        remainingSeconds = selectedMinutes * 60,
        pendingAction = null,
    )
}

internal class FocusSessionStore(
    context: Context,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences("xtraordinary_focus_state", Context.MODE_PRIVATE)

    fun load(): FocusUiState {
        val defaults = FocusUiState()
        val phase = runCatching {
            FocusPhase.valueOf(preferences.getString(PhaseKey, defaults.phase.name) ?: defaults.phase.name)
        }.getOrDefault(defaults.phase)
        val pendingAction = preferences.getString(PendingActionKey, null)?.let { value ->
            runCatching { FocusPendingAction.valueOf(value) }.getOrNull()
        }
        return PersistedFocusSession(
            task = preferences.getString(TaskKey, defaults.task) ?: defaults.task,
            selectedMinutes = preferences.getInt(SelectedMinutesKey, defaults.selectedMinutes),
            remainingSeconds = preferences.getInt(RemainingSecondsKey, defaults.remainingSeconds),
            phase = phase,
            deadlineEpochMs = preferences.getLong(DeadlineEpochMsKey, 0L),
            pendingAction = pendingAction,
        ).restoreAt(nowEpochMs())
    }

    fun save(state: FocusUiState) {
        val persisted = state.persistedAt(nowEpochMs())
        preferences.edit()
            .putString(TaskKey, persisted.task)
            .putInt(SelectedMinutesKey, persisted.selectedMinutes)
            .putInt(RemainingSecondsKey, persisted.remainingSeconds)
            .putString(PhaseKey, persisted.phase.name)
            .putLong(DeadlineEpochMsKey, persisted.deadlineEpochMs)
            .apply {
                if (persisted.pendingAction == null) remove(PendingActionKey)
                else putString(PendingActionKey, persisted.pendingAction.name)
            }
            .apply()
    }

    private companion object {
        const val TaskKey = "task"
        const val SelectedMinutesKey = "selected_minutes"
        const val RemainingSecondsKey = "remaining_seconds"
        const val PhaseKey = "phase"
        const val DeadlineEpochMsKey = "deadline_epoch_ms"
        const val PendingActionKey = "pending_action"
    }
}
