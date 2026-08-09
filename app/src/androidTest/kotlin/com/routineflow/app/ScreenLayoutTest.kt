package com.routineflow.app

import android.graphics.Color
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.routineflow.app.presentation.components.CompactPicker
import com.routineflow.app.presentation.ScreenLayout
import com.routineflow.app.presentation.screens.RUNNING_PROGRESS_BAR_HEIGHT_PX
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
    fun runningProgressBarUsesTenTimesThePreviousHeight() {
        assertEquals(80, RUNNING_PROGRESS_BAR_HEIGHT_PX)
    }
}
