package com.routineflow.app.model

enum class RecurrenceUnit { DAYS, WEEKS, MONTHS, YEARS }

sealed interface RecurrenceRule {
    data object None : RecurrenceRule
    data object Daily : RecurrenceRule
    data class Weekly(val days: Set<String>) : RecurrenceRule
    data class Monthly(val dates: Set<Int>) : RecurrenceRule
    data class Interval(
        val count: Int,
        val unit: RecurrenceUnit,
        val weekdays: Set<String> = emptySet(),
        val monthDates: Set<Int> = emptySet(),
        val weekdayPosition: String? = null,
        val weekday: String? = null
    ) : RecurrenceRule
    data class Unknown(val raw: String) : RecurrenceRule
}
