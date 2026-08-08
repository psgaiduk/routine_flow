package com.routineflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderItemsTest {
    @Test
    fun movingDownPlacesItemAfterTarget() {
        assertEquals(listOf("b", "c", "a", "d"), moveItem(listOf("a", "b", "c", "d"), 0, 2))
    }

    @Test
    fun movingUpPlacesItemBeforeTarget() {
        assertEquals(listOf("a", "d", "b", "c"), moveItem(listOf("a", "b", "c", "d"), 3, 1))
    }

    @Test
    fun invalidIndexesLeaveOrderUnchanged() {
        val items = listOf("a", "b")
        assertEquals(items, moveItem(items, -1, 1))
        assertEquals(items, moveItem(items, 0, 4))
    }
}
