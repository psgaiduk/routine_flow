package com.routineflow.app.presentation.components

import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.routineflow.app.R
import com.routineflow.app.model.Action
import com.routineflow.app.model.Chain
import com.routineflow.app.domain.RecurrenceRuleCodec

class ActionEditorDialog(
    private val context: android.content.Context,
    private val textColor: Int,
    private val inputSurfaceColor: Int,
    private val borderColor: Int,
    private val showDeleteError: (Chain, Action) -> Unit,
    private val save: (Chain, Action?, String, String, Int) -> Unit
) {
    fun show(chain: Chain, existing: Action?) {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 8) }
        fun header(text: String) = TextView(context).apply { this.text = text; textSize = 18f; setTextColor(textColor); setPadding(0, 0, 0, 12) }
        box.addView(header(context.getString(R.string.action_name)))
        val title = EditText(context).apply { tag = "action_title_input"; setText(existing?.title ?: ""); inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(true); textSize = 16f; background = null; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 0) }
        box.addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(title, LinearLayout.LayoutParams(-1, 48)); addView(View(context).apply { setBackgroundColor(0xff94a3b8.toInt()) }, LinearLayout.LayoutParams(-1, 1)) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })

        val duration = existing?.durationSeconds ?: 60
        val hours = CompactPicker(context, 0, 2, (duration / 3600).coerceAtMost(2), true, textColor)
        val minutes = CompactPicker(context, 0, 59, (duration % 3600) / 60, true, textColor)
        val seconds = CompactPicker(context, 0, 59, duration % 60, true, textColor)
        fun column(picker: CompactPicker) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; addView(picker, LinearLayout.LayoutParams(-1, 222)) }
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(column(hours), LinearLayout.LayoutParams(0, 238, 1f).apply { marginEnd = 8 })
        row.addView(separator(), LinearLayout.LayoutParams(18, 238))
        row.addView(column(minutes), LinearLayout.LayoutParams(0, 238, 1f).apply { marginStart = 8; marginEnd = 8 })
        row.addView(separator(), LinearLayout.LayoutParams(18, 238))
        row.addView(column(seconds), LinearLayout.LayoutParams(0, 238, 1f).apply { marginStart = 8 })
        val durationSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 12); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 18f; setColor(inputSurfaceColor); setStroke(1, borderColor) } }
        durationSection.addView(row, LinearLayout.LayoutParams(-1, 238))
        box.addView(header(context.getString(R.string.duration_label)))
        box.addView(durationSection, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        box.addView(labels(), LinearLayout.LayoutParams(-1, 42).apply { bottomMargin = 18 })
        var selected = existing?.recurrence?.let(RecurrenceRuleCodec::encode) ?: "NONE"
        box.addView(RecurrenceEditor(context, textColor, 0xff4f46e5.toInt(), inputSurfaceColor, borderColor).build(selected) { selected = it })

        val dialog = MaterialAlertDialogBuilder(context).setView(ScrollView(context).apply { addView(box) }).setNegativeButton(R.string.cancel, null)
            .apply { if (existing != null) setNeutralButton(R.string.delete) { _, _ -> showDeleteError(chain, existing) } }
            .setPositiveButton(R.string.save, null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (selected.startsWith("INTERVAL:") && selected.contains(":WEEKS:WEEKDAYS:") && selected.substringAfter(":WEEKS:WEEKDAYS:").isBlank()) {
                Toast.makeText(context, context.getString(R.string.select_weekday_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val totalSeconds = hours.value * 3600 + minutes.value * 60 + seconds.value
            title.text.toString().trim().takeIf(String::isNotEmpty)?.let { save(chain, existing, it, selected, totalSeconds.coerceAtLeast(1)) }
            dialog.dismiss()
        }
    }

    private fun separator() = TextView(context).apply { text = ":"; textSize = 24f; setTextColor(0xff64748b.toInt()); gravity = Gravity.CENTER; setPadding(0, 0, 0, 22) }

    private fun labels() = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        fun label(text: String) = TextView(context).apply { this.text = text; textSize = 14f; setTextColor(0xff64748b.toInt()); gravity = Gravity.CENTER }
        addView(label(context.getString(R.string.hours_short)), LinearLayout.LayoutParams(0, 42, 1f)); addView(View(context), LinearLayout.LayoutParams(18, 42))
        addView(label(context.getString(R.string.minutes_short)), LinearLayout.LayoutParams(0, 42, 1f)); addView(View(context), LinearLayout.LayoutParams(18, 42))
        addView(label(context.getString(R.string.seconds_short)), LinearLayout.LayoutParams(0, 42, 1f))
    }
}
