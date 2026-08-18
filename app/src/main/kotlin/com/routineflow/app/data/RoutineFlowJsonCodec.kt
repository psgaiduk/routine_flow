package com.routineflow.app.data

import com.routineflow.app.model.Action
import com.routineflow.app.model.Chain
import com.routineflow.app.domain.RecurrenceRuleCodec
import com.routineflow.app.domain.RoutineDay
import org.json.JSONArray
import org.json.JSONObject

/** Encodes the current JSON format and reads the legacy durationMinutes field. */
class RoutineFlowJsonCodec {
    fun decode(text: String): List<Chain> {
        val trimmed = text.trim()
        val root = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("chains") ?: JSONArray()
        return (0 until root.length()).map { index ->
            val jsonChain = root.getJSONObject(index)
            val jsonActions = jsonChain.optJSONArray("actions") ?: JSONArray()
            val actions = (0 until jsonActions.length()).map { actionIndex ->
                val jsonAction = jsonActions.getJSONObject(actionIndex)
                val durationSeconds = if (jsonAction.has("durationSeconds")) {
                    jsonAction.optInt("durationSeconds", 60)
                } else {
                    jsonAction.optInt("durationMinutes", 1) * 60
                }
                Action(
                    jsonAction.getLong("id"),
                    jsonAction.getString("title"),
                    RecurrenceRuleCodec.parse(jsonAction.getString("recurrence")),
                    durationSeconds,
                    jsonAction.optString("doneOn").takeUnless { it.isBlank() || it == "null" },
                    jsonAction.optString("executionStatus").takeUnless { it.isBlank() || it == "null" },
                    jsonAction.optLong("actualDurationSeconds").takeIf { jsonAction.has("actualDurationSeconds") && !jsonAction.isNull("actualDurationSeconds") }
                    ,jsonAction.optString("speechKey").takeUnless { it.isBlank() || it == "null" }
                    ,jsonAction.optBoolean("autoAdvance", false)
                    ,jsonAction.optString("startDate").takeUnless { it.isBlank() || it == "null" } ?: RoutineDay.currentDateKey()
                    ,jsonAction.optString("endDate").takeUnless { it.isBlank() || it == "null" }
                )
            }
            Chain(jsonChain.getLong("id"), jsonChain.getString("name"), actions)
        }
    }

    fun encode(chains: List<Chain>): String {
        val root = JSONObject().apply { put("version", JSON_VERSION); put("chains", JSONArray()) }
        val jsonChains = root.getJSONArray("chains")
        chains.forEach { chain ->
            jsonChains.put(JSONObject().apply {
                put("id", chain.id)
                put("name", chain.name)
                put("actions", JSONArray().apply {
                    chain.actions.forEach { action ->
                        put(JSONObject().apply {
                            put("id", action.id)
                            put("title", action.title)
                            put("recurrence", RecurrenceRuleCodec.encode(action.recurrence))
                            put("durationSeconds", action.durationSeconds)
                            put("doneOn", action.doneOn ?: JSONObject.NULL)
                            put("executionStatus", action.executionStatus ?: JSONObject.NULL)
                            put("actualDurationSeconds", action.actualDurationSeconds ?: JSONObject.NULL)
                            put("speechKey", action.speechKey ?: JSONObject.NULL)
                            put("autoAdvance", action.autoAdvance)
                            put("startDate", action.startDate ?: JSONObject.NULL)
                            put("endDate", action.endDate ?: JSONObject.NULL)
                        })
                    }
                })
            })
        }
        return root.toString()
    }

    private companion object { const val JSON_VERSION = 8 }
}
