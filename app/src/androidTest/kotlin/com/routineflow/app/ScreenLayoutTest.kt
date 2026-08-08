package com.routineflow.app

import android.graphics.Color
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.routineflow.app.presentation.components.CompactPicker
import com.routineflow.app.presentation.ScreenLayout
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
}
