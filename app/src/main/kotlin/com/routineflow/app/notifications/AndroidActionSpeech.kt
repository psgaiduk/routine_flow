package com.routineflow.app.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidActionSpeech @Inject constructor(
    @ApplicationContext private val context: Context
) : ActionSpeech {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val speechDirectory = File(context.filesDir, "action-speech").apply { mkdirs() }
    private var textToSpeech: TextToSpeech? = null
    private var initialized = false
    private var pendingGeneration: Pair<String, (String?) -> Unit>? = null
    private var player: MediaPlayer? = null

    init {
        mainHandler.post {
            textToSpeech = TextToSpeech(context) { status ->
                initialized = status == TextToSpeech.SUCCESS
                if (!initialized) Log.w(TAG, "TextToSpeech initialization failed: $status")
                if (initialized) flushPendingGeneration()
            }
        }
    }

    override fun generate(text: String, onReady: (String?) -> Unit) {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) { onReady(null); return }
        mainHandler.post {
            pendingGeneration = normalizedText to onReady
            if (initialized) flushPendingGeneration()
        }
    }

    override fun keyFor(text: String): String = SpeechCacheKey.forText(text, Locale.getDefault().toLanguageTag())

    override fun delete(speechKey: String) {
        mainHandler.post {
            val file = File(speechDirectory, "$speechKey.wav")
            if (file.delete()) Log.d(TAG, "deleted audio file: ${file.absolutePath}")
        }
    }

    override fun play(text: String, speechKey: String) {
        mainHandler.post {
            val file = File(speechDirectory, "$speechKey.wav")
            Log.d(TAG, "play requested: key=$speechKey exists=${file.isFile} size=${file.length()} path=${file.absolutePath}")
            if (file.isFile && file.length() > 0L) play(file) else Log.w(TAG, "audio file is missing or empty")
        }
    }

    private fun flushPendingGeneration() {
        val (text, callback) = pendingGeneration ?: return
        pendingGeneration = null
        val requestedLocale = Locale.getDefault()
        val languageTag = requestedLocale.toLanguageTag()
        val file = File(speechDirectory, "${SpeechCacheKey.forText(text, languageTag)}.wav")
        if (file.isFile && file.length() > 0L) {
            callback(file.nameWithoutExtension)
            return
        }

        val engine = textToSpeech ?: return
        val availableLocale = selectLocale(engine, requestedLocale) ?: run {
            Log.w(TAG, "No usable TextToSpeech voice for $requestedLocale")
            callback(null)
            return
        }
        engine.language = availableLocale
        val utteranceId = "cache-${System.nanoTime()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit

            override fun onDone(id: String) {
                if (id == utteranceId) {
                    val cacheId = file.nameWithoutExtension
                    file.takeIf { it.isFile && it.length() > 0L }?.let { mainHandler.post { callback(cacheId) } }
                        ?: mainHandler.post { callback(null) }
                }
            }

            override fun onError(id: String) {
                if (id == utteranceId) mainHandler.post { callback(null) }
            }
        })
        val result = runCatching { engine.synthesizeToFile(text, Bundle(), file, utteranceId) }.getOrElse {
            Log.w(TAG, "TextToSpeech generation failed", it); TextToSpeech.ERROR
        }
        if (result == TextToSpeech.ERROR) callback(null)
    }

    private fun selectLocale(engine: TextToSpeech, requested: Locale): Locale? {
        if (engine.isLanguageAvailable(requested) >= TextToSpeech.LANG_AVAILABLE) return requested
        val language = requested.language
        engine.voices.firstOrNull { it.locale.language == language }?.let { voice ->
            engine.voice = voice
            return voice.locale
        }
        return engine.defaultVoice?.locale
    }

    private fun play(file: File) {
        mainHandler.post {
            if (!file.isFile) return@post
            runCatching {
                player?.release()
                player = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    setDataSource(file.absolutePath)
                    setOnPreparedListener { prepared ->
                        Log.d(TAG, "audio prepared: duration=${prepared.duration}ms")
                        prepared.start()
                    }
                    setOnCompletionListener { completed -> completed.release(); if (player === completed) player = null }
                    setOnErrorListener { failed, what, extra ->
                        Log.e(TAG, "audio playback error: what=$what extra=$extra file=${file.absolutePath}")
                        failed.release(); if (player === failed) player = null; true
                    }
                    prepareAsync()
                }
            }.onFailure {
                Log.e(TAG, "audio playback setup failed: ${it.message}", it)
                file.delete()
            }
        }
    }

    override fun release() {
        mainHandler.post {
            player?.release()
            player = null
            textToSpeech?.shutdown()
            textToSpeech = null
            initialized = false
        }
    }

    private companion object {
        const val TAG = "AndroidActionSpeech"
    }
}
