package com.routineflow.app.presentation

import android.view.ViewGroup
import android.widget.LinearLayout

/** Layout contract for a screen that must fill the space above a fixed bottom bar. */
object ScreenLayout {
    // Keep the panel background flush with its 1.5x-sized buttons.
    const val BOTTOM_MENU_HEIGHT_PX = 200
    const val CHAIN_EDITOR_SIDE_MENU_WEIGHT = 1f
    const val CHAIN_EDITOR_CENTER_MENU_WEIGHT = 2f

    fun fillRemaining(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
}
