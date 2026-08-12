package com.routineflow.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpeechCacheKeyTest {
    @Test
    fun sameTextAndLanguageReuseTheSameAudioFileKey() {
        assertEquals(SpeechCacheKey.forText("Выпить воду", "ru-RU"), SpeechCacheKey.forText("Выпить воду", "ru-RU"))
    }

    @Test
    fun changedTextOrLanguageCreatesAnotherAudioFileKey() {
        val original = SpeechCacheKey.forText("Выпить воду", "ru-RU")

        assertNotEquals(original, SpeechCacheKey.forText("Сделать зарядку", "ru-RU"))
        assertNotEquals(original, SpeechCacheKey.forText("Выпить воду", "en-US"))
    }
}
