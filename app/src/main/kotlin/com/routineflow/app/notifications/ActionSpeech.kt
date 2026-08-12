package com.routineflow.app.notifications

interface ActionSpeech {
    fun generate(text: String, onReady: (String?) -> Unit)
    fun play(text: String, speechKey: String)
    fun keyFor(text: String): String
    fun delete(speechKey: String)
    fun release()
}
