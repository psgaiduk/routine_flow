package com.routineflow.app.presentation

import com.routineflow.app.data.ChainRepository
import com.routineflow.app.data.RoutineFlowJsonCodec
import com.routineflow.app.domain.GetTodayActionsUseCase
import com.routineflow.app.model.Action
import com.routineflow.app.model.Chain
import com.routineflow.app.model.RecurrenceRule
import com.routineflow.app.notifications.RoutineNotifier
import com.routineflow.app.notifications.ActionSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        viewModel = MainViewModel(repository, GetTodayActionsUseCase(), FakeNotifier(), FakeSpeech())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addsAndEditsChainAndAction() = runTest(dispatcher) {
        viewModel.addChain("Morning")
        advanceUntilIdle()
        val chain = repository.chains.value.single()

        viewModel.addAction(chain.id, "Water", "NONE", 60)
        advanceUntilIdle()
        val action = repository.chains.value.single().actions.single()
        viewModel.editAction(chain.id, action.id, "Stretch", "DAILY", 120)
        advanceUntilIdle()

        assertEquals("Stretch", repository.chains.value.single().actions.single().title)
        assertEquals(RecurrenceRule.Daily, repository.chains.value.single().actions.single().recurrence)
        assertEquals(120, repository.chains.value.single().actions.single().durationSeconds)
    }

    @Test
    fun movesChainsAndActionsPreservingOrder() = runTest(dispatcher) {
        repository.seed(listOf(
            Chain(1, "One", listOf(Action(11, "A", RecurrenceRule.None), Action(12, "B", RecurrenceRule.None))),
            Chain(2, "Two"),
            Chain(3, "Three")
        ))
        viewModel.moveChain(1, 3)
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L, 1L), repository.chains.value.map { it.id })

        viewModel.moveAction(1, 11, 12)
        advanceUntilIdle()
        assertEquals(listOf(12L, 11L), repository.chains.value.last().actions.map { it.id })
    }

    @Test
    fun importsJsonThroughRepository() = runTest(dispatcher) {
        val json = "[{\"id\":5,\"name\":\"Imported\",\"actions\":[]}]"
        var result = false
        viewModel.importSettings(json) { result = it }
        advanceUntilIdle()

        assertTrue(result)
        assertEquals("Imported", repository.chains.value.single().name)
    }

    @Test
    fun navigationStateTracksSelectedScreens() {
        viewModel.selectTab(AppTab.CHAINS)
        viewModel.openChain(42L)
        assertEquals(AppTab.CHAINS, viewModel.navigation.value.tab)
        assertEquals(42L, viewModel.navigation.value.chainId)

        viewModel.openExecution(42L)
        assertEquals(42L, viewModel.navigation.value.executionChainId)
        viewModel.closeExecution()
        assertEquals(null, viewModel.navigation.value.executionChainId)
    }

    @Test
    fun rewindsToPreviousCompletedStepAtItsSavedElapsedTime() = runTest(dispatcher) {
        repository.seed(listOf(
            Chain(1, "Routine", listOf(
                Action(11, "First", RecurrenceRule.Daily, durationSeconds = 30),
                Action(12, "Second", RecurrenceRule.Daily, durationSeconds = 30)
            ))
        ))

        try {
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            viewModel.startChain(1)
            runCurrent()
            advanceTimeBy(3_000)
            runCurrent()
            val elapsedBeforeCompletion = viewModel.state.value.running?.elapsedSeconds ?: error("first step is not running")

            viewModel.completeCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(12L, viewModel.state.value.running?.actionId)
            assertEquals(elapsedBeforeCompletion, repository.chains.value.single().actions.first().actualDurationSeconds)

            viewModel.rewindCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(11L, viewModel.state.value.running?.actionId)
            assertEquals(elapsedBeforeCompletion, viewModel.state.value.running?.elapsedSeconds)
            assertEquals(null, repository.chains.value.single().actions.first().doneOn)
        } finally {
            viewModel.stopAction()
            runCurrent()
        }
    }

    @Test
    fun autoAdvanceMovesToNextStepWhenPlannedDurationEnds() = runTest(dispatcher) {
        repository.seed(listOf(
            Chain(1, "Routine", listOf(
                Action(11, "First", RecurrenceRule.Daily, durationSeconds = 1),
                Action(12, "Second", RecurrenceRule.Daily, durationSeconds = 30)
            ).map { it.copy(autoAdvance = it.id == 11L) })
        ))

        try {
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            viewModel.startChain(1)
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals("DONE", repository.chains.value.single().actions.first().executionStatus)
            assertEquals(12L, viewModel.state.value.running?.actionId)
        } finally {
            viewModel.stopAction()
            runCurrent()
        }
    }

    private class FakeRepository : ChainRepository {
        private val state = MutableStateFlow<List<Chain>>(emptyList())
        override val chains: StateFlow<List<Chain>> = state
        override suspend fun replace(chains: List<Chain>) { state.value = chains }
        override suspend fun exportJson() = "[]"
        override suspend fun importJson(json: String): Boolean { state.value = RoutineFlowJsonCodec().decode(json); return true }
        fun seed(chains: List<Chain>) { state.value = chains }
    }

    private class FakeNotifier : RoutineNotifier {
        override fun notifyOvertime(chainName: String, actionName: String, reminderNumber: Int, overtimeSeconds: Long) = Unit
        override fun updateTimer(chainName: String, actionName: String, elapsedSeconds: Long, totalSeconds: Long, overtime: Boolean) = Unit
        override fun clearTimer() = Unit
    }

    private class FakeSpeech : ActionSpeech {
        override fun generate(text: String, onReady: (String?) -> Unit) = onReady(null)
        override fun play(text: String, speechKey: String) = Unit
        override fun keyFor(text: String) = text
        override fun delete(speechKey: String) = Unit
        override fun release() = Unit
    }
}
