package com.routineflow.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {
    @Test
    fun adaptiveUsesHoursWhenNeeded() = assertEquals("01:01:01", TimeFormatter.adaptive(3661))

    @Test
    fun adaptiveUsesMinutesForShorterDurations() = assertEquals("01:05", TimeFormatter.adaptive(65))

    @Test
    fun durationAlwaysUsesMinutesAndSeconds() = assertEquals("61:01", TimeFormatter.duration(3661))
}
