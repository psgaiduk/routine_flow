package com.routineflow.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.routineflow.app.model.Action
import com.routineflow.app.model.Chain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class LocalChainRepository @Inject constructor(@ApplicationContext private val context: Context) : ChainRepository {
    private val state = MutableStateFlow(load())
    override val chains = state.asStateFlow()

    override suspend fun replace(chains: List<Chain>) {
        state.value = chains
        save(chains)
    }

    @VisibleForTesting
    internal fun load(): List<Chain> = runCatching {
        val text = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        val root = JSONArray(text)
        (0 until root.length()).map { index ->
            val jsonChain = root.getJSONObject(index)
            val jsonActions = jsonChain.optJSONArray("actions") ?: JSONArray()
            val actions = (0 until jsonActions.length()).map { actionIndex ->
                val jsonAction = jsonActions.getJSONObject(actionIndex)
                val durationSeconds = if (jsonAction.has("durationSeconds")) jsonAction.optInt("durationSeconds", 20 * 60) else jsonAction.optInt("durationMinutes", 20) * 60
                Action(jsonAction.getLong("id"), jsonAction.getString("title"), jsonAction.getString("recurrence"), durationSeconds, jsonAction.optString("doneOn").takeUnless { it.isBlank() || it == "null" })
            }
            Chain(jsonChain.getLong("id"), jsonChain.getString("name"), actions)
        }
    }.getOrDefault(emptyList())

    private fun save(chains: List<Chain>) {
        val root = JSONArray()
        chains.forEach { chain ->
            root.put(JSONObject().apply {
                put("id", chain.id); put("name", chain.name)
                put("actions", JSONArray().apply { chain.actions.forEach { action ->
                    put(JSONObject().apply { put("id", action.id); put("title", action.title); put("recurrence", action.recurrence); put("durationSeconds", action.durationSeconds); put("doneOn", action.doneOn ?: JSONObject.NULL) })
                } })
            })
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { it.write(root.toString().toByteArray()) }
    }

    private companion object { const val FILE_NAME = "routine-flow.json" }
}
