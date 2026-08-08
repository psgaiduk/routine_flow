package com.routineflow.app.presentation.screens

import android.widget.LinearLayout
import android.widget.TextView
import com.routineflow.app.R

class StatsScreen(private val context: android.content.Context) {
    fun build(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply { text = context.getString(R.string.stats_placeholder); textSize = 18f; setPadding(0, 32, 0, 32) })
    }
}
