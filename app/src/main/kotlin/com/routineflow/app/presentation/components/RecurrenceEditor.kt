package com.routineflow.app.presentation.components

import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.routineflow.app.R

/** Builds the repeat-rule editor without depending on Activity state. */
class RecurrenceEditor(
    private val context: android.content.Context,
    private val textColor: Int,
    private val accentColor: Int,
    private val softSurfaceColor: Int,
    private val borderColor: Int
) {
    private val weekdays = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val weekdayResources = intArrayOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun)
    private var monthMode = 0
    private var monthDays = "1"
    private var weekdayPosition = "FIRST"
    private var weekday = "Пн"

    fun build(initial: String, onChanged: (String) -> Unit): LinearLayout {
        val units = arrayOf(R.string.unit_days, R.string.unit_weeks, R.string.unit_months).map(context::getString)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 22, 0, 0) }
        root.addView(TextView(context).apply { text = context.getString(R.string.repeat_title); textSize = 18f; setTextColor(textColor); setPadding(0, 0, 0, 12) })
        val unitPicker = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, units) }
        val count = EditText(context).apply {
            setTextSize(15f); gravity = Gravity.CENTER; setTextColor(textColor); inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2)); setSingleLine(true); setSelectAllOnFocus(true); minWidth = 48; minHeight = 44; setPadding(4, 0, 4, 0); background = null
        }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 18, 0, 0) }
        val summary = TextView(context).apply { textSize = 14f; setTextColor(0xff64748b.toInt()); setPadding(0, 10, 0, 0) }
        root.addView(details); root.addView(summary)
        val parts = initial.split(":")
        val initialUnit = when {
            initial.startsWith("WEEKLY:") -> 1
            initial.startsWith("MONTHLY:") -> 2
            initial.startsWith("INTERVAL:") -> when (parts.getOrNull(2)) { "WEEKS" -> 1; "MONTHS" -> 2; else -> 0 }
            else -> 0
        }
        var amount = if (initial.startsWith("INTERVAL:")) parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1 else 1
        monthMode = if (initial.contains(":WEEKDAY:")) 1 else 0
        monthDays = when {
            initial.startsWith("MONTHLY:") -> initial.removePrefix("MONTHLY:").ifBlank { "1" }
            initial.contains(":DATE:") -> initial.substringAfter(":DATE:").ifBlank { "1" }
            else -> "1"
        }
        weekdayPosition = initial.substringAfter(":WEEKDAY:", "FIRST").split(":").firstOrNull() ?: "FIRST"
        weekday = initial.substringAfterLast(":").takeIf { it in weekdays } ?: "Пн"

        fun localized(codes: Collection<String>) = codes.joinToString(", ") { code -> context.getString(weekdayResources[weekdays.indexOf(code).coerceAtLeast(0)]) }
        fun chips(selected: Set<String>, callback: (Set<String>) -> Unit, single: Boolean = false): View {
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER; setPadding(0, 18, 0, 8) }
            lateinit var views: List<TextView>
            fun style(view: TextView) {
                view.setTextColor(if (view.isSelected) 0xffffffff.toInt() else textColor)
                view.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (view.isSelected) accentColor else softSurfaceColor); setStroke(2, if (view.isSelected) accentColor else borderColor) }
            }
            views = weekdays.mapIndexed { index, code -> TextView(context).apply {
                text = context.getString(weekdayResources[index]); tag = code; gravity = Gravity.CENTER; textSize = 12f; isSelected = code in selected
                style(this); setOnClickListener {
                    if (single) { if (isSelected) return@setOnClickListener; views.forEach { it.isSelected = false; style(it) } }
                    isSelected = if (single) true else !isSelected; style(this)
                    callback(views.filter { it.isSelected }.map { it.tag.toString() }.toSet())
                }
            } }
            views.forEach { row.addView(it, LinearLayout.LayoutParams(0, 46, 1f).apply { marginStart = 3; marginEnd = 3 }) }
            row.post { val size = ((row.width - 42) / 7).coerceAtLeast(40); views.forEach { it.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = 3; marginEnd = 3 } } }
            return row
        }
        fun render() {
            details.removeAllViews()
            when (unitPicker.selectedItemPosition) {
                0 -> { summary.text = context.getString(R.string.repeat_interval_summary, amount, units[0]); onChanged("INTERVAL:$amount:DAYS") }
                1 -> {
                    val saved = if (initial.startsWith("WEEKLY:")) initial.removePrefix("WEEKLY:").split(",").filter(String::isNotBlank).toSet() else if (initial.startsWith("INTERVAL:")) initial.split(":").drop(4).flatMap { it.split(",") }.filter(String::isNotBlank).toSet() else emptySet()
                    details.addView(chips(saved, { selected -> summary.text = context.getString(R.string.repeat_days_summary, localized(selected)); onChanged("INTERVAL:$amount:WEEKS:WEEKDAYS:${selected.joinToString(",")}") }))
                    summary.text = context.getString(R.string.repeat_days_summary, localized(saved)); onChanged("INTERVAL:$amount:WEEKS:WEEKDAYS:${saved.joinToString(",")}")
                }
                else -> renderMonth(details, summary, ::chips, amount, onChanged, ::dayText, ::positionText)
            }
        }
        val frequency = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply { text = context.getString(R.string.repeat_every_short); textSize = 16f; setTextColor(textColor) }, LinearLayout.LayoutParams(-2, 52))
            addView(FrameLayout(context).apply { addView(count, FrameLayout.LayoutParams(-1, 52)); addView(View(context).apply { setBackgroundColor(0xff94a3b8.toInt()) }, FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM)) }, LinearLayout.LayoutParams(58, 52).apply { marginStart = 8 })
            addView(unitPicker, LinearLayout.LayoutParams(0, 52, 1f).apply { marginStart = 10 })
        }
        root.addView(frequency, 1); unitPicker.setSelection(initialUnit); count.setText(amount.toString())
        count.addTextChangedListener(SimpleTextWatcher { amount = it.toIntOrNull()?.coerceIn(1, 99) ?: 1; render() })
        unitPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) = Unit; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = render() }
        unitPicker.post { render() }
        return root
    }

    private fun unitText(index: Int) = context.getString(intArrayOf(R.string.unit_days, R.string.unit_weeks, R.string.unit_months)[index])
    private fun dayText(code: String) = context.getString(weekdayResources[weekdays.indexOf(code).coerceAtLeast(0)])
    private fun positionText(code: String?) = context.getString(when (code) { "SECOND" -> R.string.position_second; "THIRD" -> R.string.position_third; "FOURTH" -> R.string.position_fourth; "LAST" -> R.string.position_last; else -> R.string.position_first })

    private fun renderMonth(details: LinearLayout, summary: TextView, chips: (Set<String>, (Set<String>) -> Unit, Boolean) -> View, amount: Int, onChanged: (String) -> Unit, day: (String) -> String, position: (String?) -> String) {
        val mode = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val dates = RadioButton(context).apply { id = View.generateViewId(); text = context.getString(R.string.repeat_specific_number); isChecked = monthMode == 0 }
        val weekdaysMode = RadioButton(context).apply { id = View.generateViewId(); text = context.getString(R.string.repeat_weekday_mode); isChecked = monthMode == 1 }
        mode.addView(dates); mode.addView(weekdaysMode); details.addView(mode)
        val monthDetails = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 6, 0, 0) }; details.addView(monthDetails)
        fun refresh() {
            monthMode = if (weekdaysMode.isChecked) 1 else 0; monthDetails.removeAllViews()
            if (monthMode == 0) {
                val input = EditText(context).apply { hint = context.getString(R.string.month_days_hint); setText(monthDays); inputType = InputType.TYPE_CLASS_NUMBER }
                monthDetails.addView(input)
                fun update() { monthDays = input.text.toString(); summary.text = context.getString(R.string.repeat_month_summary, monthDays); onChanged("INTERVAL:$amount:MONTHS:DATE:$monthDays") }
                input.addTextChangedListener(SimpleTextWatcher { monthDays = it; summary.text = context.getString(R.string.repeat_month_summary, monthDays); onChanged("INTERVAL:$amount:MONTHS:DATE:$monthDays") }); update()
            } else {
                val codes = arrayOf("FIRST", "SECOND", "THIRD", "FOURTH", "LAST")
                val picker = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, codes.map { position(it) }); setSelection(codes.indexOf(weekdayPosition).coerceAtLeast(0)) }
                fun update() { weekdayPosition = codes[picker.selectedItemPosition.coerceIn(0, codes.lastIndex)]; summary.text = context.getString(R.string.repeat_weekday_summary, position(weekdayPosition), day(weekday)); onChanged("INTERVAL:$amount:MONTHS:WEEKDAY:$weekdayPosition:$weekday") }
                picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) = Unit; override fun onItemSelected(parent: AdapterView<*>?, view: View?, p: Int, id: Long) = update() }
                monthDetails.addView(picker); monthDetails.addView(chips(setOf(weekday), { selected -> weekday = selected.firstOrNull() ?: "Пн"; update() }, true)); update()
            }
        }
        dates.setOnClickListener { monthMode = 0; dates.isChecked = true; weekdaysMode.isChecked = false; refresh() }; weekdaysMode.setOnClickListener { monthMode = 1; dates.isChecked = false; weekdaysMode.isChecked = true; refresh() }; refresh()
    }

    private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}
