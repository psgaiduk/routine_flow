package com.routineflow.app.presentation

import android.content.Context
import android.view.ViewGroup
import android.view.View
import android.widget.ScrollView
import android.widget.LinearLayout

/** Layout contract for a screen that must fill the space above a fixed bottom bar. */
object ScreenLayout {
    // Keep the panel background flush with its 1.5x-sized buttons.
    const val BOTTOM_MENU_HEIGHT_PX = 200
    const val CHAIN_EDITOR_SIDE_MENU_WEIGHT = 1f
    const val CHAIN_EDITOR_CENTER_MENU_WEIGHT = 2f

    fun fillRemaining(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

    fun scrollable(context: Context, content: View): ScrollView = ScrollView(context).apply {
        isFillViewport = true
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
