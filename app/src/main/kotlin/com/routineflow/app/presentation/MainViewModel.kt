package com.routineflow.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routineflow.app.data.ChainRepository
import com.routineflow.app.domain.GetTodayActionsUseCase
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ChainRepository,
    private val getTodayActions: GetTodayActionsUseCase
) : ViewModel() {
    private val running = MutableStateFlow<RunningAction?>(null)
    private var timerJob: Job? = null
    val state: StateFlow<AppState> = combine(repository.chains, running) { chains, current -> AppState(chains, current) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())
    private val dateKey get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun addChain(name: String) = update { it + Chain(System.currentTimeMillis(), name) }

    fun renameChain(chainId: Long, name: String) = update { chains -> chains.map { if (it.id == chainId) it.copy(name = name) else it } }

    fun addAction(chainId: Long, title: String, recurrence: String, durationSeconds: Int) = update { chains ->
        chains.map { if (it.id == chainId) it.copy(actions = it.actions + Action(System.currentTimeMillis(), title, recurrence, durationSeconds)) else it }
    }

    fun editAction(chainId: Long, actionId: Long, title: String, recurrence: String, durationSeconds: Int) = update { chains ->
        chains.map { chain -> if (chain.id == chainId) chain.copy(actions = chain.actions.map { action -> if (action.id == actionId) action.copy(title = title, recurrence = recurrence, durationSeconds = durationSeconds) else action }) else chain }
    }

    fun toggleAction(chainId: Long, actionId: Long, checked: Boolean) = update { chains ->
        chains.map { chain -> if (chain.id == chainId) chain.copy(actions = chain.actions.map { action -> if (action.id == actionId) action.copy(doneOn = if (checked) dateKey else null) else action }) else chain }
    }

    fun deleteAction(chainId: Long, actionId: Long) = update { chains -> chains.map { if (it.id == chainId) it.copy(actions = it.actions.filterNot { action -> action.id == actionId }) else it } }
    fun deleteChain(chainId: Long) = update { chains -> chains.filterNot { it.id == chainId } }
    fun isDue(action: Action) = getTodayActions.isDue(action)

    fun startChain(chainId: Long) {
        if (running.value != null) return
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val chain = repository.chains.value.firstOrNull { it.id == chainId } ?: return@launch
            val actions = chain.actions.filter { getTodayActions.isDue(it) && it.doneOn != dateKey }
            for (action in actions) {
                var seconds = action.durationSeconds.coerceAtLeast(1).toLong()
                while (seconds >= 0L) {
                    running.value = RunningAction(chainId, action.id, seconds)
                    if (seconds == 0L) break
                    delay(1_000L); seconds--
                }
                completeAction(chainId, action.id)
            }
            running.value = null
        }
    }

    fun stopAction() { timerJob?.cancel(); timerJob = null; running.value = null }

    private suspend fun completeAction(chainId: Long, actionId: Long) {
        repository.replace(repository.chains.value.map { chain ->
            if (chain.id == chainId) chain.copy(actions = chain.actions.map { action -> if (action.id == actionId) action.copy(doneOn = dateKey) else action }) else chain
        })
    }

    private fun update(transform: (List<Chain>) -> List<Chain>) {
        viewModelScope.launch { repository.replace(transform(repository.chains.value)) }
    }
}
