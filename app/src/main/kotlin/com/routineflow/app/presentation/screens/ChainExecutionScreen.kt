package com.routineflow.app.presentation.screens

import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.routineflow.app.R
import com.routineflow.app.model.Action
import com.routineflow.app.model.AppState
import com.routineflow.app.model.Chain
import com.routineflow.app.presentation.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChainExecutionScreen(
    private val context: android.content.Context,
    private val textColor: Int,
    private val secondaryColor: Int,
    private val softSurfaceColor: Int,
    private val darkTextColor: Int,
    private val card: (View) -> MaterialCardView,
    private val bottomButton: (String, Boolean, Float, () -> Unit) -> MaterialButton,
    private val circleToggle: (Boolean, Boolean, (Boolean, ImageView) -> Unit) -> ImageView,
    private val todayKey: String,
    private val isDue: (Action) -> Boolean,
    private val onToggle: (Chain, Action, Boolean, Boolean, ImageView) -> Unit,
    private val onBack: () -> Unit,
    private val onStart: () -> Unit
) {
    fun build(state: AppState, chain: Chain): LinearLayout {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val actions = chain.actions.filter(isDue)
        val totalSeconds = actions.sumOf { it.durationSeconds }.toLong()
        val now = Calendar.getInstance(); val end = now.clone() as Calendar; end.timeInMillis += totalSeconds * 1000L
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        root.addView(TextView(context).apply { text = context.getString(R.string.run_time_range, timeFormat.format(now.time), timeFormat.format(end.time)); textSize = 15f; setTextColor(secondaryColor); setPadding(0, 0, 0, 20) })
        val allCompleted = actions.isNotEmpty() && actions.all { it.doneOn == todayKey }
        actions.forEachIndexed { index, action ->
            val completed = action.doneOn == todayKey
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(context).apply { text = "${index + 1}. ${action.title}\n${TimeFormatter.duration(action.durationSeconds.toLong())}"; textSize = 17f; setPadding(8, 12, 8, 12) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(circleToggle(completed, action.executionStatus == "SKIPPED") { checked, view ->
                onToggle(chain, action, checked, allCompleted, view)
            }, LinearLayout.LayoutParams(48, 48).apply { marginStart = 12 })
            root.addView(card(row).apply { setContentPadding(16, 10, 12, 10) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
        val running = state.running?.takeIf { it.chainId == chain.id }
        val bottom = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; background = android.graphics.drawable.GradientDrawable().apply { setColor(softSurfaceColor) } }
        if (allCompleted) {
            bottom.addView(bottomButton(context.getString(R.string.run_back), false, 16f, onBack), LinearLayout.LayoutParams(-1, 150))
        } else {
            bottom.addView(TextView(context).apply { text = "✕"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(darkTextColor); isClickable = true; background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8f; setColor(softSurfaceColor) }; setOnClickListener { onBack() } }, LinearLayout.LayoutParams(144, 150))
            val startLabel = if (running != null) TimeFormatter.duration(running.remainingSeconds) else context.getString(R.string.run_start)
            bottom.addView(bottomButton(startLabel, true, 14f, onStart), LinearLayout.LayoutParams(0, 150, 1f))
        }
        root.addView(Space(context), LinearLayout.LayoutParams(1, 0, 1f)); root.addView(bottom, LinearLayout.LayoutParams(-1, 158))
        return root
    }
}
