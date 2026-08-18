package com.routineflow.app.domain

import com.routineflow.app.model.Action
import com.routineflow.app.model.RecurrenceRule
import com.routineflow.app.model.RecurrenceUnit
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

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

private class IntervalStrategy(private val count: Long, private val unit: RecurrenceUnit, private val weekdays: Set<String> = emptySet(), private val monthDates: Set<Int> = emptySet(), private val weekdayPosition: String? = null, private val weekday: String? = null) : RecurrenceStrategy {
    override fun isDue(action: Action, day: Calendar): Boolean {
        val step = count.coerceAtLeast(1)
        val start = action.startDate?.let { parseDate(it) }
        if (start != null) {
            val daysSinceStart = daysBetween(start, day)
            if (daysSinceStart < 0) return false
            return when (unit) {
                RecurrenceUnit.DAYS -> daysSinceStart % step == 0L
                RecurrenceUnit.WEEKS -> {
                    val matchesDay = weekdays.isEmpty() || dayName(day) in weekdays
                    (daysSinceStart / 7L) % step == 0L && matchesDay
                }
                RecurrenceUnit.MONTHS -> {
                    val monthIndex = (day.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12L + day.get(Calendar.MONTH) - start.get(Calendar.MONTH)
                    if (monthIndex < 0 || monthIndex % step != 0L) false else when {
                        monthDates.isNotEmpty() -> day.get(Calendar.DAY_OF_MONTH) in monthDates
                        weekday != null -> isNthWeekday(day, weekdayPosition, weekday)
                        else -> day.get(Calendar.DAY_OF_MONTH) == start.get(Calendar.DAY_OF_MONTH)
                    }
                }
                RecurrenceUnit.YEARS -> (day.get(Calendar.YEAR) - start.get(Calendar.YEAR)).toLong() % step == 0L && day.get(Calendar.DAY_OF_YEAR) == start.get(Calendar.DAY_OF_YEAR)
            }
        }
        return when (unit) {
            RecurrenceUnit.WEEKS -> {
                val weekNumber = day.get(Calendar.WEEK_OF_YEAR).toLong()
                val matchesDay = if (weekdays.isNotEmpty()) dayName(day) in weekdays else true
                weekNumber % step == 0L && matchesDay
            }
            RecurrenceUnit.MONTHS -> {
                val monthIndex = day.get(Calendar.YEAR) * 12L + day.get(Calendar.MONTH)
                if (monthIndex % step != 0L) false else when {
                    monthDates.isNotEmpty() -> day.get(Calendar.DAY_OF_MONTH) in monthDates
                    weekday != null -> isNthWeekday(day, weekdayPosition, weekday)
                    else -> true
                }
            }
            RecurrenceUnit.YEARS -> day.get(Calendar.YEAR).toLong() % step == 0L
            else -> (day.timeInMillis / 86_400_000L) % step == 0L
        }
    }
}

private fun parseDate(value: String): Calendar? = runCatching {
    Calendar.getInstance().apply {
        clear()
        time = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)!!
    }
}.getOrNull()

private fun daysBetween(start: Calendar, day: Calendar): Long {
    val startDay = Calendar.getInstance().apply { clear(); set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH)) }
    val targetDay = Calendar.getInstance().apply { clear(); set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH)) }
    return (targetDay.timeInMillis - startDay.timeInMillis) / 86_400_000L
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
    fun create(rule: RecurrenceRule): RecurrenceStrategy = when (rule) {
        RecurrenceRule.None -> NoneStrategy
        RecurrenceRule.Daily -> DailyStrategy
        is RecurrenceRule.Weekly -> WeeklyStrategy(rule.days)
        is RecurrenceRule.Monthly -> MonthlyStrategy(rule.dates)
        is RecurrenceRule.Interval -> IntervalStrategy(rule.count.toLong(), rule.unit, rule.weekdays, rule.monthDates, rule.weekdayPosition, rule.weekday)
        is RecurrenceRule.Unknown -> NoneStrategy
    }

    fun create(rule: String): RecurrenceStrategy = when (rule) {
        "По будням" -> WeekdaysStrategy
        "Каждые 2 дня" -> LegacyIntervalStrategy
        else -> create(RecurrenceRuleCodec.parse(rule))
    }

}

private fun dayName(day: Calendar) = when (day.get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> "Пн"; Calendar.TUESDAY -> "Вт"; Calendar.WEDNESDAY -> "Ср"; Calendar.THURSDAY -> "Чт"
    Calendar.FRIDAY -> "Пт"; Calendar.SATURDAY -> "Сб"; else -> "Вс"
}
