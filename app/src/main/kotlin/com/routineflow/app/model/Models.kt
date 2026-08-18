package com.routineflow.app.model

data class Action(
    val id: Long,
    val title: String,
    val recurrence: RecurrenceRule,
    val durationSeconds: Int = 60,
    val doneOn: String? = null,
    val executionStatus: String? = null,
    val actualDurationSeconds: Long? = null,
    val speechKey: String? = null,
    val autoAdvance: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null
)
data class Chain(val id: Long, val name: String, val actions: List<Action> = emptyList())
data class RunningAction(val chainId: Long, val actionId: Long, val totalSeconds: Long, val remainingSeconds: Long, val estimatedEndMillis: Long, val elapsedSeconds: Long = 0L, val overtime: Boolean = false, val paused: Boolean = false)
data class AppState(val chains: List<Chain> = emptyList(), val running: RunningAction? = null)
