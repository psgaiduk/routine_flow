package com.routineflow.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSignalPlaybackTest {
    @Test
    fun playbackStartsWhenReadyAndReleasesOnlyWhenSoundCompletes() {
        val events = mutableListOf<String>()
        val playback = AlarmSignalPlayback(
            start = { events += "start" },
            release = { events += "release" }
        )

        playback.onPrepared()
        assertEquals(listOf("start"), events)

        playback.onCompletion()
        assertEquals(listOf("start", "release"), events)
    }
}
