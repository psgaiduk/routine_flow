package com.routineflow.app.domain

object OvertimeReminderSchedule {
    const val MIN_FIRST_REMINDER_DELAY_SECONDS = 30L

    fun firstReminderDelaySeconds(totalSeconds: Long): Long =
        maxOf(MIN_FIRST_REMINDER_DELAY_SECONDS, (totalSeconds.coerceAtLeast(1L) + 4L) / 5L)
}
