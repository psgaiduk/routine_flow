package com.routineflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OvertimeReminderScheduleTest {
    @Test
    fun oneMinuteUsesThirtySecondMinimum() {
        assertEquals(30L, OvertimeReminderSchedule.firstReminderDelaySeconds(60L))
    }

    @Test
    fun tenMinutesUsesTwentyPercent() {
        assertEquals(120L, OvertimeReminderSchedule.firstReminderDelaySeconds(600L))
    }

    @Test
    fun shortDurationsUseMinimum() {
        assertEquals(30L, OvertimeReminderSchedule.firstReminderDelaySeconds(1L))
        assertEquals(30L, OvertimeReminderSchedule.firstReminderDelaySeconds(150L))
    }

    @Test
    fun fractionalTwentyPercentRoundsUp() {
        assertEquals(31L, OvertimeReminderSchedule.firstReminderDelaySeconds(151L))
    }
}
