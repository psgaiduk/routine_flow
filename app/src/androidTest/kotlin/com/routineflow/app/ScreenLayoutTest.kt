package com.routineflow.app

import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
