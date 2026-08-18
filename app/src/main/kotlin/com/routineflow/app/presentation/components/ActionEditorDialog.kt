package com.routineflow.app.presentation.components

import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import android.app.DatePickerDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.routineflow.app.R
import com.routineflow.app.model.Action
import com.routineflow.app.model.Chain
import com.routineflow.app.domain.RecurrenceRuleCodec
import com.routineflow.app.notifications.ActionSpeech
import com.routineflow.app.domain.RoutineDay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ActionEditorDialog(
    private val context: android.content.Context,
    private val textColor: Int,
    private val inputSurfaceColor: Int,
    private val borderColor: Int,
    private val actionSpeech: ActionSpeech,
    private val showDeleteError: (Chain, Action) -> Unit,
    private val save: (Chain, Action?, String, String, Int, String?, Boolean, String?) -> Unit
) {
    fun show(chain: Chain, existing: Action?) {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 8) }
        fun header(text: String) = TextView(context).apply { this.text = text; textSize = 18f; setTextColor(textColor); setPadding(0, 0, 0, 12) }
        box.addView(header(context.getString(R.string.action_name)))
        val title = EditText(context).apply { tag = "action_title_input"; setText(existing?.title ?: ""); inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(true); textSize = 16f; background = null; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 0) }
        box.addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(title, LinearLayout.LayoutParams(-1, 48)); addView(View(context).apply { setBackgroundColor(0xff94a3b8.toInt()) }, LinearLayout.LayoutParams(-1, 1)) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })

        var speechKey: String? = existing?.speechKey?.takeIf { it == actionSpeech.keyFor(title.text.toString()) }
        val soundCheck = CheckBox(context).apply {
            text = context.getString(R.string.action_add_sound)
            isChecked = speechKey != null
        }
        box.addView(soundCheck, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        val soundStatus = TextView(context).apply {
            text = if (speechKey != null) context.getString(R.string.action_sound_ready) else ""
            textSize = 13f
            setTextColor(0xff64748b.toInt())
        }
        box.addView(soundStatus, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })

        val autoAdvanceCheck = CheckBox(context).apply {
            tag = "action_auto_advance_checkbox"
            text = context.getString(R.string.action_auto_advance)
            isChecked = existing?.autoAdvance == true
        }
        box.addView(autoAdvanceCheck, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })

        fun dateField(tagValue: String, hintText: String, initial: String?): EditText {
            return EditText(context).apply {
                tag = tagValue
                hint = hintText
                setText(initial ?: "")
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_DATETIME
                minHeight = 80
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = true
                setPadding(12, 8, 12, 8)
                setOnClickListener {
                    val calendar = Calendar.getInstance()
                    initial?.takeIf { it.isNotBlank() }?.let {
                        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it) }.getOrNull()?.let(calendar::setTime)
                    }
                    DatePickerDialog(context, { _, year, month, day ->
                        setText("%04d-%02d-%02d".format(Locale.US, year, month + 1, day))
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                }
            }
        }
        fun fieldLabel(text: String) = TextView(context).apply { this.text = text; textSize = 14f; setTextColor(textColor); setPadding(0, 8, 0, 2) }
        val startDate = dateField("action_start_date_input", context.getString(R.string.action_date_hint), existing?.startDate ?: RoutineDay.currentDateKey())
        box.addView(fieldLabel(context.getString(R.string.action_start_date)))
        box.addView(startDate, LinearLayout.LayoutParams(-1, 88))

        val duration = existing?.durationSeconds ?: 60
        val hours = CompactPicker(context, 0, 2, (duration / 3600).coerceAtMost(2), true, textColor)
        val minutes = CompactPicker(context, 0, 59, (duration % 3600) / 60, true, textColor)
        val seconds = CompactPicker(context, 0, 59, duration % 60, true, textColor)
        fun column(picker: CompactPicker) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; addView(picker, LinearLayout.LayoutParams(-1, 244)) }
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(column(hours), LinearLayout.LayoutParams(0, 260, 1f).apply { marginEnd = 8 })
        row.addView(separator(), LinearLayout.LayoutParams(18, 260))
        row.addView(column(minutes), LinearLayout.LayoutParams(0, 260, 1f).apply { marginStart = 8; marginEnd = 8 })
        row.addView(separator(), LinearLayout.LayoutParams(18, 260))
        row.addView(column(seconds), LinearLayout.LayoutParams(0, 260, 1f).apply { marginStart = 8 })
        val durationSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 12); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 18f; setColor(inputSurfaceColor); setStroke(1, borderColor) } }
        durationSection.addView(row, LinearLayout.LayoutParams(-1, 260))
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
            title.text.toString().trim().takeIf(String::isNotEmpty)?.let { save(chain, existing, it, selected, totalSeconds.coerceAtLeast(1), speechKey, autoAdvanceCheck.isChecked, startDate.text.toString().trim().takeIf(String::isNotEmpty)) }
            dialog.dismiss()
        }
        val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        var updatingSoundCheck = false
        fun refreshSoundState() {
            val valid = speechKey == actionSpeech.keyFor(title.text.toString().trim())
            if (!valid) speechKey = null
            saveButton.isEnabled = !soundCheck.isChecked || valid
            soundStatus.text = if (valid && soundCheck.isChecked) context.getString(R.string.action_sound_ready) else ""
        }
        title.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { soundCheck.isChecked = false; refreshSoundState() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        soundCheck.setOnCheckedChangeListener { _, checked ->
            if (updatingSoundCheck) return@setOnCheckedChangeListener
            if (!checked) { speechKey = null; soundStatus.text = ""; saveButton.isEnabled = true; return@setOnCheckedChangeListener }
            saveButton.isEnabled = false
            soundStatus.text = context.getString(R.string.action_sound_generating)
            actionSpeech.generate(title.text.toString().trim()) { generatedKey ->
                speechKey = generatedKey
                updatingSoundCheck = true
                soundCheck.isChecked = generatedKey != null
                updatingSoundCheck = false
                saveButton.isEnabled = generatedKey != null
                soundStatus.text = if (generatedKey != null) context.getString(R.string.action_sound_ready) else context.getString(R.string.action_sound_error)
                if (generatedKey == null) Toast.makeText(context, R.string.action_sound_error, Toast.LENGTH_SHORT).show()
            }
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
