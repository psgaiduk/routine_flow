package com.routineflow.app.domain

enum class RecurrenceType { NONE, DAILY, WEEKLY, MONTHLY, INTERVAL, UNKNOWN }

data class ParsedRecurrence(
    val type: RecurrenceType,
    val amount: Int = 1,
    val unit: String? = null,
    val days: List<String> = emptyList(),
    val monthDates: List<String> = emptyList(),
    val weekdayPosition: String? = null,
    val weekday: String? = null,
    val original: String
)

/** Parses persisted recurrence strings without tying the domain to Android resources. */
object RecurrenceRuleParser {
    fun parse(rule: String): ParsedRecurrence {
        return when {
            rule == "NONE" -> ParsedRecurrence(RecurrenceType.NONE, original = rule)
            rule == "DAILY" -> ParsedRecurrence(RecurrenceType.DAILY, original = rule)
            rule.startsWith("WEEKLY:") -> ParsedRecurrence(
                type = RecurrenceType.WEEKLY,
                days = rule.removePrefix("WEEKLY:").split(",").filter(String::isNotBlank),
                original = rule
            )
            rule.startsWith("MONTHLY:") -> ParsedRecurrence(
                type = RecurrenceType.MONTHLY,
                monthDates = rule.removePrefix("MONTHLY:").split(",").filter(String::isNotBlank),
                original = rule
            )
            rule.startsWith("INTERVAL:") -> parseInterval(rule)
            else -> ParsedRecurrence(RecurrenceType.UNKNOWN, original = rule)
        }
    }

    private fun parseInterval(rule: String): ParsedRecurrence {
        val parts = rule.split(":")
        val amount = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val unit = parts.getOrNull(2) ?: "DAYS"
        val extra = parts.drop(3)
        return when {
            unit == "WEEKS" && extra.firstOrNull() == "WEEKDAYS" -> ParsedRecurrence(
                RecurrenceType.INTERVAL, amount, unit,
                days = extra.drop(1).flatMap { it.split(",") }.filter(String::isNotBlank), original = rule
            )
            unit == "MONTHS" && extra.firstOrNull() == "DATE" -> ParsedRecurrence(
                RecurrenceType.INTERVAL, amount, unit,
                monthDates = extra.drop(1).flatMap { it.split(",") }.filter(String::isNotBlank), original = rule
            )
            unit == "MONTHS" && extra.firstOrNull() == "WEEKDAY" -> ParsedRecurrence(
                RecurrenceType.INTERVAL, amount, unit,
                weekdayPosition = extra.getOrNull(1), weekday = extra.getOrNull(2), original = rule
            )
            else -> ParsedRecurrence(RecurrenceType.INTERVAL, amount, unit, original = rule)
        }
    }
}
