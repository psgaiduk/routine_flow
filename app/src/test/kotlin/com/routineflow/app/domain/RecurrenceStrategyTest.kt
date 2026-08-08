package com.routineflow.app.domain

import com.routineflow.app.model.Action
import com.routineflow.app.model.RecurrenceRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurrenceStrategyTest {
    private val action = Action(1L, "Test", RecurrenceRule.None, 60)

    @Test
    fun dailyIsDueEveryDay() {
        assertTrue(isDue("DAILY", 2026, Calendar.AUGUST, 8))
    }

    @Test
    fun weeklyIntervalMatchesSelectedDays() {
        assertTrue(isDue("INTERVAL:1:WEEKS:WEEKDAYS:Пн,Ср", 2026, Calendar.AUGUST, 10))
        assertTrue(isDue("INTERVAL:1:WEEKS:WEEKDAYS:Пн,Ср", 2026, Calendar.AUGUST, 12))
        assertFalse(isDue("INTERVAL:1:WEEKS:WEEKDAYS:Пн,Ср", 2026, Calendar.AUGUST, 11))
    }

    @Test
    fun monthlyDateMatchesOnlyConfiguredDates() {
        assertTrue(isDue("INTERVAL:1:MONTHS:DATE:10,25", 2026, Calendar.AUGUST, 25))
        assertFalse(isDue("INTERVAL:1:MONTHS:DATE:10,25", 2026, Calendar.AUGUST, 24))
    }

    @Test
    fun monthlyNthWeekdayMatchesPosition() {
        assertTrue(isDue("INTERVAL:1:MONTHS:WEEKDAY:FIRST:Пн", 2026, Calendar.JUNE, 1))
        assertFalse(isDue("INTERVAL:1:MONTHS:WEEKDAY:FIRST:Пн", 2026, Calendar.JUNE, 8))
    }

    private fun isDue(rule: String, year: Int, month: Int, dayOfMonth: Int): Boolean {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, 12, 0, 0)
        }
        return RecurrenceStrategyFactory.create(rule).isDue(action, calendar)
    }
}
