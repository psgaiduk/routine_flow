package com.routineflow.app.domain

import com.routineflow.app.model.Action
import com.routineflow.app.model.RecurrenceRule
import com.routineflow.app.model.RecurrenceUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ActionScheduleTest {
    private val useCase = GetTodayActionsUseCase()

    @Test
    fun startDateLimitsActionAvailability() {
        val action = Action(1L, "Step", RecurrenceRule.Daily, startDate = "2026-08-10")

        assertFalse(useCase.isDue(action, day(2026, Calendar.AUGUST, 9)))
        assertTrue(useCase.isDue(action, day(2026, Calendar.AUGUST, 10)))
    }

    @Test
    fun dayIntervalIsCalculatedFromStartDate() {
        val action = Action(1L, "Step", RecurrenceRule.Interval(3, RecurrenceUnit.DAYS), startDate = "2026-08-10")

        assertTrue(useCase.isDue(action, day(2026, Calendar.AUGUST, 10)))
        assertFalse(useCase.isDue(action, day(2026, Calendar.AUGUST, 12)))
        assertTrue(useCase.isDue(action, day(2026, Calendar.AUGUST, 13)))
    }

    @Test
    fun monthlyDateUsesTheNextMatchingDateAfterStart() {
        val action = Action(1L, "Step", RecurrenceRule.Interval(1, RecurrenceUnit.MONTHS, monthDates = setOf(5, 15)), startDate = "2026-08-10")

        assertFalse(useCase.isDue(action, day(2026, Calendar.AUGUST, 5)))
        assertTrue(useCase.isDue(action, day(2026, Calendar.AUGUST, 15)))
        assertTrue(useCase.isDue(action, day(2026, Calendar.SEPTEMBER, 5)))
    }

    @Test
    fun monthlyWeekdayUsesTheNextMatchingWeekdayAfterStart() {
        val action = Action(1L, "Step", RecurrenceRule.Interval(1, RecurrenceUnit.MONTHS, weekdayPosition = "FIRST", weekday = "Пн"), startDate = "2026-08-10")

        assertFalse(useCase.isDue(action, day(2026, Calendar.AUGUST, 3)))
        assertTrue(useCase.isDue(action, day(2026, Calendar.SEPTEMBER, 7)))
    }

    private fun day(year: Int, month: Int, day: Int): Calendar = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 12, 0, 0)
    }
}
