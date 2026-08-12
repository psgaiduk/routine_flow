package com.routineflow.app.notifications

import java.security.MessageDigest

object SpeechCacheKey {
    fun forText(text: String, languageTag: String): String = sha256("$languageTag\u0000$text")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
