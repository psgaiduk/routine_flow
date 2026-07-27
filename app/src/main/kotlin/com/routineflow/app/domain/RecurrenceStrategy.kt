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

private class IntervalStrategy(private val count: Long, private val unit: String) : RecurrenceStrategy {
    override fun isDue(action: Action, day: Calendar): Boolean {
        val period = when (unit) { "WEEKS" -> count * 7; "MONTHS" -> count * 30; else -> count }.coerceAtLeast(1)
        return (day.timeInMillis / 86_400_000L) % period == 0L
    }
}

object RecurrenceStrategyFactory {
    fun create(rule: String): RecurrenceStrategy = when {
        rule == "NONE" || rule == "Не повторять" -> NoneStrategy
        rule == "DAILY" || rule == "Каждый день" -> DailyStrategy
        rule == "По будням" -> WeekdaysStrategy
        rule == "Каждые 2 дня" -> LegacyIntervalStrategy
        rule.startsWith("WEEKLY:") -> WeeklyStrategy(rule.removePrefix("WEEKLY:").split(",").filter(String::isNotBlank).toSet())
        rule.startsWith("MONTHLY:") -> MonthlyStrategy(rule.removePrefix("MONTHLY:").split(",").mapNotNull(String::toIntOrNull).toSet())
        rule.startsWith("INTERVAL:") -> rule.split(":").let { IntervalStrategy(it.getOrNull(1)?.toLongOrNull() ?: 1, it.getOrNull(2) ?: "DAYS") }
        rule.startsWith("Дни: ") -> WeeklyStrategy(rule.removePrefix("Дни: ").split(", ").toSet())
        else -> NoneStrategy
    }

}

private fun dayName(day: Calendar) = when (day.get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> "Пн"; Calendar.TUESDAY -> "Вт"; Calendar.WEDNESDAY -> "Ср"; Calendar.THURSDAY -> "Чт"
    Calendar.FRIDAY -> "Пт"; Calendar.SATURDAY -> "Сб"; else -> "Вс"
}
