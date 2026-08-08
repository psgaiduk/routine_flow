package com.routineflow.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.routineflow.app.model.Chain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class LocalChainRepository @Inject constructor(@ApplicationContext private val context: Context) : ChainRepository {
    private val codec = RoutineFlowJsonCodec()
    private val state = MutableStateFlow(load())
    override val chains = state.asStateFlow()

    override suspend fun replace(chains: List<Chain>) {
        state.value = chains
        save(chains)
    }

    override suspend fun exportJson(): String = codec.encode(state.value)

    override suspend fun importJson(json: String): Boolean = runCatching {
        val imported = codec.decode(json)
        state.value = imported
        save(imported)
    }.isSuccess

    @VisibleForTesting
    internal fun load(): List<Chain> = runCatching {
        val text = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        codec.decode(text)
    }.getOrDefault(emptyList())

    private fun save(chains: List<Chain>) {
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { it.write(codec.encode(chains).toByteArray()) }
    }

    private companion object { const val FILE_NAME = "routine-flow.json" }
}
