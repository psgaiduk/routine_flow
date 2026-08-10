package com.routineflow.app.notifications

/** Keeps alarm playback tied to the sound's lifecycle instead of a fixed timeout. */
internal class AlarmSignalPlayback(
    private val start: () -> Unit,
    private val release: () -> Unit
) {
    fun onPrepared() = start()

    fun onCompletion() = release()
}
