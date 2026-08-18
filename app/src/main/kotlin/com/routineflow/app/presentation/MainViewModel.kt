package com.routineflow.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routineflow.app.data.ChainRepository
import com.routineflow.app.domain.GetTodayActionsUseCase
import com.routineflow.app.domain.OvertimeReminderSchedule
import com.routineflow.app.domain.moveItem
import com.routineflow.app.domain.RecurrenceRuleCodec
import com.routineflow.app.domain.RoutineDay
import com.routineflow.app.notifications.RoutineNotifier
import com.routineflow.app.notifications.ActionSpeech
import com.routineflow.app.model.Action
import com.routineflow.app.model.AppState
import com.routineflow.app.model.Chain
import com.routineflow.app.model.RunningAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ChainRepository,
    private val getTodayActions: GetTodayActionsUseCase,
    private val overtimeNotifier: RoutineNotifier,
    private val actionSpeech: ActionSpeech
) : ViewModel() {
    private data class CompletedStep(val action: Action, val elapsedSeconds: Long)

    private val _navigation = MutableStateFlow(NavigationState())
    val navigation: StateFlow<NavigationState> = _navigation
    private val running = MutableStateFlow<RunningAction?>(null)
    private var timerJob: Job? = null
    private var postponeRequested = false
    private var completionStatus = "DONE"
    private var completionRequested = false
    private var requestedCompletionElapsed: Long? = null
    private var rewindRequested = false
    private val completedSteps = mutableListOf<CompletedStep>()
    val state: StateFlow<AppState> = combine(repository.chains, running) { chains, current -> AppState(chains, current) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())
    private val dateKey get() = RoutineDay.currentDateKey()

    fun addChain(name: String) = update { it + Chain(System.currentTimeMillis(), name) }

    fun exportSettings(onReady: (String) -> Unit) = viewModelScope.launch {
        onReady(repository.exportJson())
    }

    fun importSettings(json: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repository.importJson(json))
    }

    fun moveChain(fromChainId: Long, toChainId: Long) = update { chains ->
        val fromIndex = chains.indexOfFirst { it.id == fromChainId }
        val toIndex = chains.indexOfFirst { it.id == toChainId }
        moveItem(chains, fromIndex, toIndex)
    }

    fun moveChainToEnd(chainId: Long) = update { chains ->
        val index = chains.indexOfFirst { it.id == chainId }
        if (index < 0 || index == chains.lastIndex) chains else chains.toMutableList().apply { add(removeAt(index)) }
    }

    fun renameChain(chainId: Long, name: String) = update { chains -> chains.map { if (it.id == chainId) it.copy(name = name) else it } }

    fun addAction(chainId: Long, title: String, recurrence: String, durationSeconds: Int, speechKey: String? = null, autoAdvance: Boolean = false, startDate: String? = null) = update { chains ->
        chains.map { if (it.id == chainId) it.copy(actions = it.actions + Action(System.currentTimeMillis(), title, RecurrenceRuleCodec.parse(recurrence), durationSeconds, speechKey = speechKey, autoAdvance = autoAdvance, startDate = startDate ?: dateKey)) else it }
    }

    fun editAction(chainId: Long, actionId: Long, title: String, recurrence: String, durationSeconds: Int, speechKey: String? = null, autoAdvance: Boolean = false, startDate: String? = null) = update { chains ->
        chains.map { chain -> if (chain.id == chainId) chain.copy(actions = chain.actions.map { action -> if (action.id == actionId) action.copy(title = title, recurrence = RecurrenceRuleCodec.parse(recurrence), durationSeconds = durationSeconds, speechKey = speechKey, autoAdvance = autoAdvance, startDate = startDate ?: dateKey) else action }) else chain }
    }

    fun toggleAction(chainId: Long, actionId: Long, checked: Boolean) = update { chains ->
        chains.map { chain -> if (chain.id == chainId) chain.copy(actions = chain.actions.map { action ->
            if (action.id == actionId) action.copy(
                doneOn = if (checked) dateKey else null,
                executionStatus = if (checked) "DONE" else null,
                actualDurationSeconds = null
            ) else action
        }) else chain }
    }

    fun deleteAction(chainId: Long, actionId: Long) = update { chains -> chains.map { if (it.id == chainId) it.copy(actions = it.actions.filterNot { action -> action.id == actionId }) else it } }
    fun moveAction(chainId: Long, fromActionId: Long, toActionId: Long) = update { chains ->
        chains.map { chain ->
            if (chain.id != chainId) chain else {
                val fromIndex = chain.actions.indexOfFirst { it.id == fromActionId }
                val toIndex = chain.actions.indexOfFirst { it.id == toActionId }
                chain.copy(actions = moveItem(chain.actions, fromIndex, toIndex))
            }
        }
    }

    fun moveActionToEnd(chainId: Long, actionId: Long) = update { chains ->
        chains.map { chain ->
            if (chain.id != chainId) chain else {
                val index = chain.actions.indexOfFirst { it.id == actionId }
                if (index < 0 || index == chain.actions.lastIndex) chain else chain.copy(actions = chain.actions.toMutableList().apply { add(removeAt(index)) })
            }
        }
    }
    fun deleteChain(chainId: Long) = update { chains -> chains.filterNot { it.id == chainId } }
    fun isDue(action: Action) = getTodayActions.isDue(action)

    fun selectTab(tab: AppTab) { _navigation.value = NavigationState(tab = tab) }
    fun openChain(chainId: Long) { _navigation.value = _navigation.value.copy(chainId = chainId, executionChainId = null) }
    fun openExecution(chainId: Long) { _navigation.value = _navigation.value.copy(executionChainId = chainId) }
    fun closeExecution() { _navigation.value = _navigation.value.copy(executionChainId = null) }

    fun startChain(chainId: Long) {
        if (running.value != null) return
        postponeRequested = false
        completionStatus = "DONE"
        completionRequested = false
        requestedCompletionElapsed = null
        rewindRequested = false
        completedSteps.clear()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val chain = repository.chains.value.firstOrNull { it.id == chainId } ?: return@launch
            val pending = chain.actions.filter { getTodayActions.isDue(it) && it.doneOn != dateKey }.toMutableList()
            var estimatedEndMillis = System.currentTimeMillis() + pending.sumOf { it.durationSeconds }.toLong() * 1000L
            while (pending.isNotEmpty()) {
                var action = pending.removeAt(0)
                var elapsed = 0L
                var postponed = false
                var overtimeSignalSent = false
                var reminderNumber = 0
                var reminderInterval = OvertimeReminderSchedule.firstReminderDelaySeconds(action.durationSeconds.toLong())
                var nextReminderAt = action.durationSeconds.coerceAtLeast(1).toLong() + reminderInterval
                action.speechKey?.let { actionSpeech.play(action.title, it) }
                while (true) {
                    while (running.value?.paused == true) {
                        if (postponeRequested) break
                        delay(100L)
                    }
                    if (postponeRequested) {
                        postponeRequested = false; pending.add(action); running.value = null; postponed = true; break
                    }
                    if (rewindRequested) {
                        rewindRequested = false
                        val previous = completedSteps.removeLastOrNull()
                        if (previous != null) {
                            clearActionCompletion(chainId, previous.action.id)
                            pending.add(0, action)
                            action = previous.action
                            elapsed = previous.elapsedSeconds
                            completionStatus = "DONE"
                            continue
                        }
                    }
                    if (completionRequested || (action.autoAdvance && elapsed >= action.durationSeconds.coerceAtLeast(1))) {
                        if (action.autoAdvance && elapsed >= action.durationSeconds.coerceAtLeast(1)) overtimeNotifier.playCompletionAlarm()
                        completionRequested = false
                        elapsed = requestedCompletionElapsed ?: elapsed
                        requestedCompletionElapsed = null
                        break
                    }
                    val total = action.durationSeconds.coerceAtLeast(1).toLong()
                    if (action.autoAdvance && total - elapsed in 1L..3L) overtimeNotifier.playCountdownBeep()
                    val remaining = (total - elapsed).coerceAtLeast(0L)
                    running.value = RunningAction(chainId, action.id, total, remaining, estimatedEndMillis, elapsed, elapsed >= total)
                    overtimeNotifier.updateTimer(chain.name, action.title, elapsed, total, elapsed >= total)
                    if (!overtimeSignalSent && elapsed >= total) {
                        overtimeNotifier.notifyOvertime(chain.name, action.title, 0, elapsed - total)
                        overtimeSignalSent = true
                    } else if (overtimeSignalSent && elapsed >= nextReminderAt) {
                        reminderNumber += 1
                        overtimeNotifier.notifyOvertime(chain.name, action.title, reminderNumber, elapsed - total)
                        nextReminderAt = elapsed + reminderInterval * 2
                        reminderInterval *= 2
                    }
                    delay(1_000L)
                    elapsed = running.value?.elapsedSeconds?.plus(1L) ?: elapsed + 1L
                }
                if (postponed) continue
                running.value = RunningAction(chainId, action.id, action.durationSeconds.toLong(), 0L, estimatedEndMillis)
                completeAction(chainId, action.id, completionStatus, elapsed)
                completedSteps.add(CompletedStep(action, elapsed))
                completionStatus = "DONE"
                estimatedEndMillis = System.currentTimeMillis() + pending.sumOf { it.durationSeconds }.toLong() * 1000L
            }
            running.value = null
            completedSteps.clear()
            overtimeNotifier.clearTimer()
        }
    }

    fun stopAction() {
        postponeRequested = false
        timerJob?.cancel(); timerJob = null; running.value = null; overtimeNotifier.clearTimer()
    }

    fun pauseResume() {
        running.value?.let { running.value = it.copy(paused = !it.paused) }
    }

    fun resetCurrentTimer() {
        running.value?.let { current ->
            val action = repository.chains.value.firstOrNull { it.id == current.chainId }?.actions?.firstOrNull { it.id == current.actionId }
            if (action != null) running.value = current.copy(totalSeconds = action.durationSeconds.toLong(), remainingSeconds = action.durationSeconds.toLong(), elapsedSeconds = 0L, overtime = false, paused = false)
        }
    }

    fun canRewindCurrent(): Boolean = running.value != null && completedSteps.isNotEmpty()

    fun rewindCurrent() {
        if (!canRewindCurrent()) return
        rewindRequested = true
        running.value?.let { running.value = it.copy(paused = false) }
    }

    fun postponeCurrent() { postponeRequested = true; running.value?.let { running.value = it.copy(paused = false) } }

    fun completeCurrent() {
        completionStatus = "DONE"
        requestedCompletionElapsed = running.value?.elapsedSeconds
        completionRequested = true
        running.value?.let { current -> running.value = current.copy(paused = false) }
    }

    fun skipCurrent() {
        completionStatus = "SKIPPED"
        requestedCompletionElapsed = running.value?.elapsedSeconds
        completionRequested = true
        running.value?.let { current -> running.value = current.copy(paused = false) }
    }

    private suspend fun completeAction(chainId: Long, actionId: Long, status: String, actualDurationSeconds: Long) {
        repository.replace(repository.chains.value.map { chain ->
            if (chain.id == chainId) chain.copy(actions = chain.actions.map { action -> if (action.id == actionId) action.copy(doneOn = dateKey, executionStatus = status, actualDurationSeconds = actualDurationSeconds) else action }) else chain
        })
    }

    private suspend fun clearActionCompletion(chainId: Long, actionId: Long) {
        repository.replace(repository.chains.value.map { chain ->
            if (chain.id == chainId) chain.copy(actions = chain.actions.map { action ->
                if (action.id == actionId) action.copy(doneOn = null, executionStatus = null, actualDurationSeconds = null) else action
            }) else chain
        })
    }

    private fun update(transform: (List<Chain>) -> List<Chain>) {
        viewModelScope.launch { repository.replace(transform(repository.chains.value)) }
    }
}
