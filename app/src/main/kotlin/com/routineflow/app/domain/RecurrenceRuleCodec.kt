package com.routineflow.app.domain

import com.routineflow.app.model.RecurrenceRule
import com.routineflow.app.model.RecurrenceUnit

object RecurrenceRuleCodec {
    fun parse(raw: String): RecurrenceRule {
        return when {
            raw == "NONE" || raw == "Не повторять" -> RecurrenceRule.None
            raw == "DAILY" || raw == "Каждый день" -> RecurrenceRule.Daily
            raw.startsWith("WEEKLY:") -> RecurrenceRule.Weekly(raw.removePrefix("WEEKLY:").split(",").filter(String::isNotBlank).toSet())
            raw.startsWith("MONTHLY:") -> RecurrenceRule.Monthly(raw.removePrefix("MONTHLY:").split(",").mapNotNull(String::toIntOrNull).toSet())
            raw.startsWith("INTERVAL:") -> parseInterval(raw)
            else -> RecurrenceRule.Unknown(raw)
        }
    }

    fun encode(rule: RecurrenceRule): String = when (rule) {
        RecurrenceRule.None -> "NONE"
        RecurrenceRule.Daily -> "DAILY"
        is RecurrenceRule.Weekly -> "WEEKLY:${rule.days.joinToString(",")}"
        is RecurrenceRule.Monthly -> "MONTHLY:${rule.dates.sorted().joinToString(",")}"
        is RecurrenceRule.Interval -> encodeInterval(rule)
        is RecurrenceRule.Unknown -> rule.raw
    }

    private fun parseInterval(raw: String): RecurrenceRule {
        val parts = raw.split(":")
        val count = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val unit = when (parts.getOrNull(2)) { "WEEKS" -> RecurrenceUnit.WEEKS; "MONTHS" -> RecurrenceUnit.MONTHS; "YEARS" -> RecurrenceUnit.YEARS; else -> RecurrenceUnit.DAYS }
        val extra = parts.drop(3)
        return when {
            unit == RecurrenceUnit.WEEKS && extra.firstOrNull() == "WEEKDAYS" -> RecurrenceRule.Interval(count, unit, weekdays = extra.drop(1).flatMap { it.split(",") }.filter(String::isNotBlank).toSet())
            unit == RecurrenceUnit.MONTHS && extra.firstOrNull() == "DATE" -> RecurrenceRule.Interval(count, unit, monthDates = extra.drop(1).flatMap { it.split(",") }.mapNotNull(String::toIntOrNull).toSet())
            unit == RecurrenceUnit.MONTHS && extra.firstOrNull() == "WEEKDAY" -> RecurrenceRule.Interval(count, unit, weekdayPosition = extra.getOrNull(1), weekday = extra.getOrNull(2))
            else -> RecurrenceRule.Interval(count, unit)
        }
    }

    private fun encodeInterval(rule: RecurrenceRule.Interval): String {
        val prefix = "INTERVAL:${rule.count}:${rule.unit.name}"
        return when {
            rule.unit == RecurrenceUnit.WEEKS && rule.weekdays.isNotEmpty() -> "$prefix:WEEKDAYS:${rule.weekdays.joinToString(",")}"
            rule.unit == RecurrenceUnit.MONTHS && rule.monthDates.isNotEmpty() -> "$prefix:DATE:${rule.monthDates.sorted().joinToString(",")}"
            rule.unit == RecurrenceUnit.MONTHS && rule.weekday != null -> "$prefix:WEEKDAY:${rule.weekdayPosition ?: "FIRST"}:${rule.weekday}"
            else -> prefix
        }
    }
}
