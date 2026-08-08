package com.routineflow.app.presentation

import android.view.ViewGroup
import android.widget.LinearLayout

/** Layout contract for a screen that must fill the space above a fixed bottom bar. */
object ScreenLayout {
    fun fillRemaining(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
}
