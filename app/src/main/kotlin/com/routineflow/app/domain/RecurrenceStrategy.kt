package com.routineflow.app.domain

import com.routineflow.app.model.Action
import java.util.Calendar

interface RecurrenceStrategy { fun isDue(action: Action, day: Calendar): Boolean }

private object NoneStrategy : RecurrenceStrategy { override fun isDue(action: Action, day: Calendar) = false }
private object DailyStrategy : RecurrenceStrategy { override fun isDue(action: Action, day: Calendar) = true }
private object WeekdaysStrategy : RecurrenceStrategy { override fun isDue(action: Action, day: Calendar) = day.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.SATURDAY, Calendar.SUNDAY) }
private object LegacyIntervalStrategy : RecurrenceStrategy { override fun isDue(action: Action, day: Calendar) = (day.timeInMillis / 86_400_000L) % 2L == 0L }

private class WeeklyStrategy(private val days: Set<String>) : RecurrenceStrategy {
    override fun isDue(action: Action, day: Calendar): Boolean = dayName(day) in days
}

private class MonthlyStrategy(private val days: Set<Int>) : RecurrenceStrategy {
    override fun isDue(action: Action, day: Calendar): Boolean = day.get(Calendar.DAY_OF_MONTH) in days
}

private class IntervalStrategy(private val count: Long, private val unit: String, private val extra: List<String> = emptyList()) : RecurrenceStrategy {
    override fun isDue(action: Action, day: Calendar): Boolean {
        val step = count.coerceAtLeast(1)
        return when (unit) {
            "WEEKS" -> {
                val weekNumber = day.get(Calendar.WEEK_OF_YEAR).toLong()
                val selectedDays = extra.drop(1).flatMap { it.split(",") }.filter(String::isNotBlank).toSet()
                val matchesDay = if (extra.firstOrNull() == "WEEKDAYS") dayName(day) in selectedDays else true
                weekNumber % step == 0L && matchesDay
            }
            "MONTHS" -> {
                val monthIndex = day.get(Calendar.YEAR) * 12L + day.get(Calendar.MONTH)
                if (monthIndex % step != 0L) false else when (extra.firstOrNull()) {
                    "DATE" -> day.get(Calendar.DAY_OF_MONTH).toString() in extra.drop(1).flatMap { it.split(",") }
                    "WEEKDAY" -> isNthWeekday(day, extra.getOrNull(1), extra.getOrNull(2))
                    else -> true
                }
            }
            "YEARS" -> day.get(Calendar.YEAR).toLong() % step == 0L
            else -> (day.timeInMillis / 86_400_000L) % step == 0L
        }
    }
}

private fun isNthWeekday(day: Calendar, position: String?, code: String?): Boolean {
    if (dayName(day) != code) return false
    val occurrence = (day.get(Calendar.DAY_OF_MONTH) - 1) / 7 + 1
    if (position == "LAST") {
        val next = day.clone() as Calendar
        next.add(Calendar.DAY_OF_MONTH, 7)
        return next.get(Calendar.MONTH) != day.get(Calendar.MONTH)
    }
    val expected = when (position) { "SECOND" -> 2; "THIRD" -> 3; "FOURTH" -> 4; else -> 1 }
    return occurrence == expected
}

object RecurrenceStrategyFactory {
    fun create(rule: String): RecurrenceStrategy = when {
        rule == "NONE" || rule == "Не повторять" -> NoneStrategy
        rule == "DAILY" || rule == "Каждый день" -> DailyStrategy
        rule == "По будням" -> WeekdaysStrategy
        rule == "Каждые 2 дня" -> LegacyIntervalStrategy
        rule.startsWith("WEEKLY:") -> WeeklyStrategy(rule.removePrefix("WEEKLY:").split(",").filter(String::isNotBlank).toSet())
        rule.startsWith("MONTHLY:") -> MonthlyStrategy(rule.removePrefix("MONTHLY:").split(",").mapNotNull(String::toIntOrNull).toSet())
        rule.startsWith("INTERVAL:") -> rule.split(":").let { IntervalStrategy(it.getOrNull(1)?.toLongOrNull() ?: 1, it.getOrNull(2) ?: "DAYS", it.drop(3)) }
        rule.startsWith("Дни: ") -> WeeklyStrategy(rule.removePrefix("Дни: ").split(", ").toSet())
        else -> NoneStrategy
    }

}

private fun dayName(day: Calendar) = when (day.get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> "Пн"; Calendar.TUESDAY -> "Вт"; Calendar.WEDNESDAY -> "Ср"; Calendar.THURSDAY -> "Чт"
    Calendar.FRIDAY -> "Пт"; Calendar.SATURDAY -> "Сб"; else -> "Вс"
}
