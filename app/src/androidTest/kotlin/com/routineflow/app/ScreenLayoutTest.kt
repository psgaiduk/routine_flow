package com.routineflow.app

import android.graphics.Color
import android.widget.TextView
import android.widget.LinearLayout
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.routineflow.app.model.Action
import com.routineflow.app.model.AppState
import com.routineflow.app.model.Chain
import com.routineflow.app.model.RecurrenceRule
import com.routineflow.app.presentation.components.CompactPicker
import com.routineflow.app.presentation.ScreenLayout
import com.routineflow.app.presentation.screens.RUNNING_PROGRESS_BAR_HEIGHT_PX
import com.routineflow.app.presentation.screens.ChainExecutionScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenLayoutTest {
    @Test
    fun screenUsesRemainingHeightAboveFixedBottomBar() {
        val params = ScreenLayout.fillRemaining()

        assertEquals(-1, params.width)
        assertEquals(0, params.height)
        assertTrue(params.weight == 1f)
    }

    @Test
    fun allBottomMenusUseSharedScaledDimensions() {
        assertEquals(200, ScreenLayout.BOTTOM_MENU_HEIGHT_PX)
    }

    @Test
    fun scrollableContentKeepsContentInDedicatedScrollContainer() {
        val content = LinearLayout(ApplicationProvider.getApplicationContext())
        val scroll = ScreenLayout.scrollable(ApplicationProvider.getApplicationContext(), content)

        assertEquals(1, scroll.childCount)
        assertEquals(content, scroll.getChildAt(0))
    }

    @Test
    fun chainEditorMenuUsesTwentyFiveFiftyTwentyFiveWidthSplit() {
        val totalWeight = ScreenLayout.CHAIN_EDITOR_SIDE_MENU_WEIGHT * 2 + ScreenLayout.CHAIN_EDITOR_CENTER_MENU_WEIGHT

        assertEquals(25f, ScreenLayout.CHAIN_EDITOR_SIDE_MENU_WEIGHT / totalWeight * 100f, 0.001f)
        assertEquals(50f, ScreenLayout.CHAIN_EDITOR_CENTER_MENU_WEIGHT / totalWeight * 100f, 0.001f)
    }

    @Test
    fun durationPickerBalancesActiveValueWithoutClippingShift() {
        val picker = CompactPicker(
            ApplicationProvider.getApplicationContext(),
            0,
            59,
            10,
            true,
            Color.WHITE
        )
        val activeValue = picker.getChildAt(1) as TextView

        assertEquals(0f, activeValue.translationY)
        assertTrue(activeValue.includeFontPadding)
        assertEquals(84, activeValue.layoutParams.height)
        assertEquals(8, activeValue.paddingBottom)
        assertEquals(60, picker.getChildAt(0).layoutParams.height)
        assertEquals(72, picker.getChildAt(2).layoutParams.height)
    }

    @Test
    fun completedActionShowsPlannedAndActualDurations() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val action = Action(1L, "Step", RecurrenceRule.None, 60, "2026-08-10", "DONE", 92L)
        val screen = ChainExecutionScreen(
            context = context,
            textColor = Color.WHITE,
            secondaryColor = Color.GRAY,
            softSurfaceColor = Color.DKGRAY,
            darkTextColor = Color.WHITE,
            card = { content -> MaterialCardView(context).apply { addView(content) } },
            bottomButton = { text, _, _, _ -> MaterialButton(context).apply { this.text = text } },
            circleToggle = { _, _, _ -> ImageView(context) },
            todayKey = "2026-08-10",
            isDue = { true },
            onToggle = { _, _, _, _, _ -> },
            onBack = {},
            onStart = {}
        )

        val root = screen.build(AppState(), Chain(1L, "Routine", listOf(action)))
        val duration = findTextView(root) { it.text.toString().contains("01:32 (01:00)") }

        assertTrue(duration?.text?.toString()?.contains("01:32 (01:00)") == true)
    }

    @Test
    fun runningProgressBarUsesTenTimesThePreviousHeight() {
        assertEquals(80, RUNNING_PROGRESS_BAR_HEIGHT_PX)
    }

    private fun findTextView(view: View, predicate: (TextView) -> Boolean): TextView? {
        if (view is TextView && predicate(view)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTextView(view.getChildAt(index), predicate)?.let { return it }
            }
        }
        return null
    }
}
