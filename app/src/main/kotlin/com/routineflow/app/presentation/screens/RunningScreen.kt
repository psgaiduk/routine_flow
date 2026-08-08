package com.routineflow.app.presentation.screens

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.routineflow.app.R
import com.routineflow.app.model.Action
import com.routineflow.app.model.Chain
import com.routineflow.app.model.RunningAction
import com.routineflow.app.presentation.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RunningScreenResult(val root: LinearLayout, val timer: TextView, val progress: ProgressBar, val pause: MaterialButton)

class RunningScreen(
    private val context: android.content.Context,
    private val textColor: Int,
    private val secondaryColor: Int,
    private val accentColor: Int,
    private val softSurfaceColor: Int,
    private val darkTextColor: Int,
    private val base: (String) -> LinearLayout,
    private val card: (View) -> MaterialCardView,
    private val secondaryButton: (String, () -> Unit) -> MaterialButton,
    private val controlButton: (String, Boolean, Float, () -> Unit) -> MaterialButton,
    private val isDue: (Action) -> Boolean,
    private val todayKey: String,
    private val onStop: () -> Unit,
    private val onBack: () -> Unit,
    private val onReset: () -> Unit,
    private val onPostpone: () -> Unit,
    private val onPauseResume: () -> Unit,
    private val onComplete: () -> Unit,
    private val onSkip: () -> Unit
) {
    fun build(chain: Chain, current: RunningAction, canGoBack: Boolean): RunningScreenResult {
        val action = chain.actions.firstOrNull { it.id == current.actionId } ?: error("Running action not found")
        val actions = chain.actions.filter(isDue); val next = actions.drop(actions.indexOfFirst { it.id == action.id } + 1).firstOrNull { it.doneOn != todayKey }
        val root = base(chain.name); val remaining = actions.count { it.doneOn != todayKey }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 12) }
        header.addView(TextView(context).apply { text = context.getString(R.string.running_remaining, remaining); textSize = 14f; setTextColor(secondaryColor) }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.forEach { step -> val color = when { step.doneOn == todayKey && step.executionStatus == "SKIPPED" -> 0xffef4444.toInt(); step.doneOn == todayKey -> 0xff22c55e.toInt(); else -> accentColor }; header.addView(TextView(context).apply { text = "●"; textSize = 18f; setTextColor(color); setPadding(2, 0, 2, 0) }) }
        header.addView(TextView(context).apply { text = "✕"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(darkTextColor); setPadding(12, 0, 0, 0); isClickable = true; setOnClickListener { onStop() } }); root.addView(header)
        val currentCard = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 18) }
        currentCard.addView(TextView(context).apply { text = context.getString(R.string.running_duration, TimeFormatter.adaptive(current.totalSeconds)); textSize = 14f; setTextColor(secondaryColor) })
        currentCard.addView(TextView(context).apply { text = action.title; textSize = 26f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(textColor); setPadding(0, 10, 0, 10) })
        val timer = TextView(context).apply { text = TimeFormatter.adaptive(current.elapsedSeconds); textSize = 36f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(if (current.overtime) 0xffef4444.toInt() else accentColor); gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 8, 0, 14) }; currentCard.addView(timer)
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = current.totalSeconds.coerceAtLeast(1).toInt(); this.progress = current.elapsedSeconds.coerceAtMost(current.totalSeconds).toInt(); progressTintList = ColorStateList.valueOf(if (current.overtime) 0xffef4444.toInt() else accentColor); progressBackgroundTintList = ColorStateList.valueOf(softSurfaceColor) }; currentCard.addView(progress, LinearLayout.LayoutParams(-1, 20))
        root.addView(card(currentCard).apply { tag = "running_current_card"; radius = 24f; strokeWidth = 2; strokeColor = accentColor }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        if (canGoBack) root.addView(secondaryButton(context.getString(R.string.running_back_step), onBack))
        root.addView(secondaryButton(context.getString(R.string.running_reset), onReset).apply { tag = "running_reset_button" }); root.addView(secondaryButton(context.getString(R.string.running_postpone), onPostpone))
        val nextCard = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }; nextCard.addView(TextView(context).apply { text = context.getString(R.string.running_next); textSize = 14f; setTextColor(secondaryColor) }); nextCard.addView(TextView(context).apply { text = next?.title ?: context.getString(R.string.running_last_step); textSize = 20f; setTextColor(textColor); setPadding(0, 6, 0, 0) }); root.addView(card(nextCard))
        val bottom = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }; bottom.addView(TextView(context).apply { text = context.getString(R.string.running_end, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(current.estimatedEndMillis))); textSize = 14f; gravity = Gravity.CENTER; setTextColor(secondaryColor); setPadding(0, 0, 0, 8) })
        val controls = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }; val pause = controlButton(if (current.paused) context.getString(R.string.running_resume) else context.getString(R.string.running_pause), false, 14f, onPauseResume); controls.addView(pause, LinearLayout.LayoutParams(0, 150, 1f)); controls.addView(controlButton("✓", true, 28f, onComplete), LinearLayout.LayoutParams(0, 150, 1f)); controls.addView(controlButton(context.getString(R.string.running_skip), false, 14f, onSkip), LinearLayout.LayoutParams(0, 150, 1f)); bottom.addView(controls); root.addView(Space(context), LinearLayout.LayoutParams(1, 0, 1f)); root.addView(bottom, LinearLayout.LayoutParams(-1, 210))
        return RunningScreenResult(root, timer, progress, pause)
    }
}
