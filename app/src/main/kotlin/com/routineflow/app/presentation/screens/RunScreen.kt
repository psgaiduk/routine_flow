package com.routineflow.app.presentation.screens

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.card.MaterialCardView
import com.routineflow.app.R
import com.routineflow.app.model.AppState
import com.routineflow.app.model.Chain
import com.routineflow.app.presentation.TimeFormatter

class RunScreen(
    private val context: android.content.Context,
    private val textColor: Int,
    private val secondaryColor: Int,
    private val accentColor: Int,
    private val borderColor: Int,
    private val card: (View) -> MaterialCardView,
    private val isDue: (com.routineflow.app.model.Action) -> Boolean,
    private val todayKey: String,
    private val isCompletedExpanded: () -> Boolean,
    private val onToggleCompleted: () -> Unit,
    private val onOpen: (Chain, AppState) -> Unit,
    private val onQuickStart: (Chain) -> Unit
) {
    fun build(state: AppState): LinearLayout {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val today = state.chains.filter { chain -> chain.actions.any(isDue) }
        val completed = today.filter { chain -> chain.actions.filter(isDue).all { it.doneOn == todayKey } }
        val active = today - completed.toSet()
        root.addView(TextView(context).apply { text = if (active.isEmpty()) { if (completed.isEmpty()) context.getString(R.string.run_no_chains) else context.getString(R.string.run_no_active_chains) } else context.getString(R.string.run_subtitle); textSize = 15f; setPadding(0, 0, 0, 12) })
        active.forEach { root.addView(chainCard(it, state), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }) }
        if (completed.isNotEmpty()) {
            root.addView(TextView(context).apply { text = context.getString(if (isCompletedExpanded()) R.string.run_completed_collapse else R.string.run_completed_expand, completed.size); textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(textColor); setPadding(4, 20, 4, 12); isClickable = true; setOnClickListener { onToggleCompleted() } })
            if (isCompletedExpanded()) completed.forEach { root.addView(chainCard(it, state), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }) }
        }
        return root
    }

    private fun chainCard(chain: Chain, state: AppState): View {
        val due = chain.actions.filter(isDue)
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(4, 12, 4, 12) }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(TextView(context).apply { text = context.getString(R.string.run_chain_meta, TimeFormatter.duration(due.sumOf { it.durationSeconds }.toLong()), due.size); textSize = 13f; setTextColor(secondaryColor) })
        details.addView(TextView(context).apply { text = chain.name; textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(textColor); setPadding(0, 4, 0, 0) })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageButton(context).apply { setImageResource(android.R.drawable.ic_media_play); imageTintList = ColorStateList.valueOf(accentColor); contentDescription = context.getString(R.string.quick_start); background = null; setPadding(12, 12, 4, 12); setOnClickListener { onQuickStart(chain) } }, LinearLayout.LayoutParams(58, 58).apply { marginStart = 12 })
        row.setOnClickListener { onOpen(chain, state) }; details.setOnClickListener { onOpen(chain, state) }
        return card(row).apply { radius = 22f; strokeWidth = 2; strokeColor = borderColor; isClickable = true; isFocusable = true; setOnClickListener { onOpen(chain, state) } }
    }
}
