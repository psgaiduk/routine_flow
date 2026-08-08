package com.routineflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceRuleParserTest {
    @Test
    fun parsesWeeklyDays() {
        val result = RecurrenceRuleParser.parse("INTERVAL:2:WEEKS:WEEKDAYS:Пн,Ср")
        assertEquals(RecurrenceType.INTERVAL, result.type)
        assertEquals(2, result.amount)
        assertEquals(listOf("Пн", "Ср"), result.days)
    }

    @Test
    fun parsesMonthlyWeekday() {
        val result = RecurrenceRuleParser.parse("INTERVAL:1:MONTHS:WEEKDAY:FIRST:Пн")
        assertEquals("FIRST", result.weekdayPosition)
        assertEquals("Пн", result.weekday)
    }

    @Test
    fun preservesUnknownRule() {
        val result = RecurrenceRuleParser.parse("future-rule")
        assertEquals(RecurrenceType.UNKNOWN, result.type)
        assertEquals("future-rule", result.original)
    }
}
