package com.routineflow.app.presentation

import android.content.Context
import com.routineflow.app.R
import com.routineflow.app.domain.RecurrenceRuleParser
import com.routineflow.app.domain.RecurrenceRuleCodec
import com.routineflow.app.domain.RecurrenceType
import com.routineflow.app.model.RecurrenceRule

class RecurrenceDisplayFormatter(private val context: Context) {
    fun format(rule: RecurrenceRule): String {
        val parsed = RecurrenceRuleParser.parse(RecurrenceRuleCodec.encode(rule))
        return when (parsed.type) {
            RecurrenceType.NONE -> context.getString(R.string.repeat_none_summary)
            RecurrenceType.DAILY -> context.getString(R.string.repeat_daily_summary)
            RecurrenceType.WEEKLY -> context.getString(R.string.repeat_days_summary, localizedDays(parsed.days))
            RecurrenceType.MONTHLY -> context.getString(R.string.repeat_month_summary, parsed.monthDates.joinToString(", "))
            RecurrenceType.INTERVAL -> formatInterval(parsed)
            RecurrenceType.UNKNOWN -> RecurrenceRuleCodec.encode(rule)
        }
    }

    private fun formatInterval(parsed: com.routineflow.app.domain.ParsedRecurrence): String {
        val unit = when (parsed.unit) {
            "WEEKS" -> R.string.unit_weeks
            "MONTHS" -> R.string.unit_months
            else -> R.string.unit_days
        }
        return when {
            parsed.days.isNotEmpty() -> context.getString(
                R.string.repeat_interval_weekdays_summary,
                parsed.amount,
                context.getString(unit),
                localizedDays(parsed.days)
            )
            parsed.weekday != null -> context.getString(
                R.string.repeat_month_weekday_interval_summary,
                parsed.amount,
                context.getString(unit),
                context.getString(positionResource(parsed.weekdayPosition)),
                context.getString(dayResource(parsed.weekday.orEmpty()))
            )
            parsed.monthDates.isNotEmpty() -> context.getString(
                R.string.repeat_month_dates_interval_summary,
                parsed.amount,
                context.getString(unit),
                parsed.monthDates.joinToString(", ")
            )
            else -> context.getString(R.string.repeat_interval_summary, parsed.amount, context.getString(unit))
        }
    }

    private fun localizedDays(codes: Collection<String>): String = codes.joinToString(", ") { context.getString(dayResource(it)) }

    private fun dayResource(code: String): Int = mapOf(
        "Пн" to R.string.day_mon, "Вт" to R.string.day_tue, "Ср" to R.string.day_wed,
        "Чт" to R.string.day_thu, "Пт" to R.string.day_fri, "Сб" to R.string.day_sat,
        "Вс" to R.string.day_sun
    )[code] ?: R.string.day_mon

    private fun positionResource(position: String?): Int = when (position) {
        "SECOND" -> R.string.position_second
        "THIRD" -> R.string.position_third
        "FOURTH" -> R.string.position_fourth
        "LAST" -> R.string.position_last
        else -> R.string.position_first
    }
}
