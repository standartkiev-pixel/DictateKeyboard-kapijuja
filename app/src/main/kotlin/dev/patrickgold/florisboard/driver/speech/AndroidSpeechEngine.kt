/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.driver.speech

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Android system text-to-speech engine (default). Uses whichever engine the user picked
 * (Google, Samsung, RHVoice…) or the system default, and one of its voices or the language setting.
 *
 * One [TextToSpeech] instance is kept and reused while the engine choice stays the same, because
 * binding to an engine takes noticeable time; it is shut down when the choice changes.
 */
class AndroidSpeechEngine(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var ttsEngine: String? = null
    private var utteranceSession = 0L

    private companion object {
        /** About two sentences: short enough that resuming after a pause repeats little. */
        const val PIECE_MAX = 400
    }

    /**
     * Speaks [text] and returns when it has been spoken; cancelling stops it at once.
     *
     * The platform engine cannot pause, so a pause is a stop that remembers its place: the text is
     * queued in sentence-sized pieces, and when [paused] turns false again the reading starts over
     * from the piece that was interrupted.
     */
    suspend fun speak(
        text: String,
        settings: SpeechSettings,
        paused: StateFlow<Boolean>,
        onStarted: () -> Unit,
    ) {
        var spoken = false
        try {
            speakOnce(text, settings, paused) {
                spoken = true
                onStarted()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A cached TextToSpeech object may have lost its Android service binding.
            // Rebind once, but do not replay audio after any utterance was already heard.
            if (spoken) throw e
            withContext(Dispatchers.Main) { shutdown() }
            delay(250)
            speakOnce(text, settings, paused, onStarted)
        }
    }

    private suspend fun speakOnce(
        text: String,
        settings: SpeechSettings,
        paused: StateFlow<Boolean>,
        onStarted: () -> Unit,
    ) {
        val engine = obtain(settings.androidEnginePackage)
        withContext(Dispatchers.Main) { configure(engine, settings) }
        val maxLength = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceIn(200, PIECE_MAX)
        val pieces = SpeechTextChunker.split(text, firstChunkMax = maxLength, chunkMax = maxLength)
        if (pieces.isEmpty()) return
        // Our own audio session for the engine's output, so the loudness boost can be attached to it.
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val audioSession = audioManager?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE
        val loudness = SpeechAudio.loudness(audioSession, settings.boostDb)
        val params = Bundle().apply {
            if (audioSession > 0) {
                putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, audioSession)
            }
        }
        try {
            var next = 0
            while (next < pieces.size) {
                paused.first { !it }
                next = speakFrom(engine, pieces, next, params, paused, onStarted)
            }
        } finally {
            runCatching { loudness?.release() }
        }
    }

    /**
     * Queues `pieces[from..]` and waits. Returns the index to continue from: the interrupted piece
     * when the wait ended because of a pause, otherwise `pieces.size`.
     */
    private suspend fun speakFrom(
        engine: TextToSpeech,
        pieces: List<String>,
        from: Int,
        params: Bundle,
        paused: StateFlow<Boolean>,
        onStarted: () -> Unit,
    ): Int = coroutineScope {
        val prefix = "kapijuja-${++utteranceSession}-"
        val lastId = prefix + pieces.lastIndex
        val current = AtomicInteger(from)
        val finished = AtomicBoolean(false)
        // Engine callbacks arrive on its own threads, several at once after a stop: answer only once.
        val answered = AtomicBoolean(false)
        val pauseWatch = launch {
            paused.first { it }
            runCatching { engine.stop() }
        }
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    private var announced = false
                    override fun onStart(utteranceId: String?) {
                        val id = utteranceId ?: return
                        if (!id.startsWith(prefix)) return
                        id.removePrefix(prefix).toIntOrNull()?.let { current.set(it) }
                        if (!announced) {
                            announced = true
                            onStarted()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == lastId) {
                            finished.set(true)
                            if (answered.compareAndSet(false, true)) cont.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId?.startsWith(prefix) == true && answered.compareAndSet(false, true)) {
                            cont.resumeWithException(SpeechEngineException("Android TTS error $errorCode"))
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId?.startsWith(prefix) == true && answered.compareAndSet(false, true)) cont.resume(Unit)
                    }
                })
                for (index in from..pieces.lastIndex) {
                    val mode = if (index == from) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val result = engine.speak(pieces[index], mode, params, prefix + index)
                    if (result != TextToSpeech.SUCCESS) {
                        if (answered.compareAndSet(false, true)) {
                            cont.resumeWithException(SpeechEngineException("Android TTS refused the text"))
                        }
                        return@suspendCancellableCoroutine
                    }
                }
                cont.invokeOnCancellation { runCatching { engine.stop() } }
            }
        } finally {
            pauseWatch.cancel()
        }
        if (paused.value && !finished.get()) current.get() else pieces.size
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    /** Installed TTS engines: package name and label. */
    suspend fun engines(): List<SpeechVoice> {
        val engine = obtain("")
        return engine.engines.map { SpeechVoice(it.name, it.label ?: it.name) }
    }

    /** Voices of [enginePackage] (blank = system default) with readable labels, see [AndroidVoiceLabels]. */
    suspend fun voices(enginePackage: String, preferredLanguage: String = ""): List<SpeechVoice> {
        val engine = obtain(enginePackage)
        val voices = withContext(Dispatchers.Main) { runCatching { engine.voices }.getOrNull().orEmpty() }
        return voices
            .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .map { AndroidVoiceInfo(it.name, it.locale, it.isNetworkConnectionRequired) }
            .let { AndroidVoiceLabels.describe(appContext, it, preferredLanguage) }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        ttsEngine = null
    }

    private suspend fun obtain(enginePackage: String): TextToSpeech = mutex.withLock {
        val wanted = enginePackage.ifBlank { null }
        tts?.let { if (ttsEngine == wanted) return@withLock it }
        shutdown()
        val created = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var instance: TextToSpeech? = null
                var earlyStatus: Int? = null
                fun complete(status: Int) {
                    val ready = instance
                    if (status == TextToSpeech.SUCCESS && ready != null) {
                        if (cont.isActive) cont.resume(ready) else runCatching { ready.shutdown() }
                    } else {
                        runCatching { ready?.shutdown() }
                        if (cont.isActive) {
                            cont.resumeWithException(SpeechEngineException("Android TTS engine is not available"))
                        }
                    }
                }
                instance = TextToSpeech(appContext, { status ->
                    if (instance == null) earlyStatus = status else complete(status)
                }, wanted)
                earlyStatus?.let { complete(it) }
                cont.invokeOnCancellation { runCatching { instance?.shutdown() } }
            }
        }
        tts = created
        ttsEngine = wanted
        created
    }

    private fun configure(engine: TextToSpeech, settings: SpeechSettings) {
        engine.setAudioAttributes(SpeechAudio.attributes)
        engine.setSpeechRate(settings.speed.coerceIn(0.25f, 4f))
        engine.setPitch(settings.androidPitch.coerceIn(0.25f, 4f))
        val voice = settings.androidVoice.takeIf { it.isNotBlank() }?.let { name ->
            runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == name }
        }
        if (voice != null) {
            engine.setVoice(voice)
        } else {
            engine.setLanguage(Locale.forLanguageTag(settings.language))
        }
    }
}
