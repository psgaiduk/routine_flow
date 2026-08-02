package com.routineflow.app.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Business-day clock. Keep this value configurable so it can later come from app settings.
 */
object RoutineDay {
    var dayStartHour: Int = 4

    fun currentCalendar(): Calendar = Calendar.getInstance().apply {
        if (get(Calendar.HOUR_OF_DAY) < dayStartHour) {
            add(Calendar.DAY_OF_YEAR, -1)
        }
    }

    fun currentDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .format(currentCalendar().time)
}
