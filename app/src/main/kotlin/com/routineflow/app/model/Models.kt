package com.routineflow.app.model

data class Action(val id: Long, val title: String, val recurrence: String, val durationSeconds: Int = 20 * 60, val doneOn: String? = null)
data class Chain(val id: Long, val name: String, val actions: List<Action> = emptyList())
data class RunningAction(val chainId: Long, val actionId: Long, val remainingSeconds: Long)
data class AppState(val chains: List<Chain> = emptyList(), val running: RunningAction? = null)
