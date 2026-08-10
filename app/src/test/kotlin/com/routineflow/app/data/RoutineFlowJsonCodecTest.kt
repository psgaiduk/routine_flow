package com.routineflow.app.data

import com.routineflow.app.model.Action
import com.routineflow.app.model.Chain
import com.routineflow.app.domain.RecurrenceRuleCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineFlowJsonCodecTest {
    private val codec = RoutineFlowJsonCodec()

    @Test
    fun roundTripPreservesOrderAndFields() {
        val source = listOf(
            Chain(10L, "Morning", listOf(Action(11L, "Water", RecurrenceRuleCodec.parse("INTERVAL:1:WEEKS:WEEKDAYS:Пн,Ср"), 75, "2026-08-08", "DONE", 92L))),
            Chain(20L, "Evening")
        )

        val result = codec.decode(codec.encode(source))

        assertEquals(source, result)
    }

    @Test
    fun decodesLegacyDurationMinutes() {
        val result = codec.decode("[{\"id\":1,\"name\":\"Legacy\",\"actions\":[{\"id\":2,\"title\":\"Step\",\"recurrence\":\"NONE\",\"durationMinutes\":3}]}]")

        assertEquals(180, result.single().actions.single().durationSeconds)
    }

    @Test
    fun emptyListIsEncodedAsVersionedDocument() {
        val encoded = codec.encode(emptyList())
        assertTrue(encoded.contains("\"version\":3"))
        assertTrue(encoded.contains("\"chains\":[]"))
    }

    @Test
    fun readsLegacyRootArray() {
        val result = codec.decode("[{\"id\":7,\"name\":\"Old\",\"actions\":[]}]")
        assertEquals("Old", result.single().name)
    }

    @Test
    fun oldActionsWithoutActualDurationRemainCompatible() {
        val result = codec.decode("[{\"id\":1,\"name\":\"Legacy\",\"actions\":[{\"id\":2,\"title\":\"Step\",\"recurrence\":\"NONE\",\"durationSeconds\":60}]}]")

        assertEquals(null, result.single().actions.single().actualDurationSeconds)
    }
}
