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
import kotlinx.coroutines.Dispatchers
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

    /** Speaks [text] and returns when it has been spoken; cancelling stops it at once. */
    suspend fun speak(text: String, settings: SpeechSettings, onStarted: () -> Unit) {
        val engine = obtain(settings.androidEnginePackage)
        withContext(Dispatchers.Main) { configure(engine, settings) }
        val maxLength = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceIn(200, 3900)
        val pieces = SpeechTextChunker.split(text, firstChunkMax = maxLength, chunkMax = maxLength)
        if (pieces.isEmpty()) return
        val session = ++utteranceSession
        val lastId = "kapijuja-$session-${pieces.lastIndex}"
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
            speakPieces(engine, pieces, params, session, lastId, onStarted)
        } finally {
            runCatching { loudness?.release() }
        }
    }

    private suspend fun speakPieces(
        engine: TextToSpeech,
        pieces: List<String>,
        params: Bundle,
        session: Long,
        lastId: String,
        onStarted: () -> Unit,
    ) {
        suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                private var announced = false
                override fun onStart(utteranceId: String?) {
                    if (!announced && utteranceId?.startsWith("kapijuja-$session-") == true) {
                        announced = true
                        onStarted()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == lastId && cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId?.startsWith("kapijuja-$session-") == true && cont.isActive) {
                        cont.resumeWithException(SpeechEngineException("Android TTS error $errorCode"))
                    }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId?.startsWith("kapijuja-$session-") == true && cont.isActive) cont.resume(Unit)
                }
            })
            pieces.forEachIndexed { index, piece ->
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val result = engine.speak(piece, mode, params, "kapijuja-$session-$index")
                if (result != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resumeWithException(SpeechEngineException("Android TTS refused the text"))
                    return@suspendCancellableCoroutine
                }
            }
            cont.invokeOnCancellation { runCatching { engine.stop() } }
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    /** Installed TTS engines: package name and label. */
    suspend fun engines(): List<SpeechVoice> {
        val engine = obtain("")
        return engine.engines.map { SpeechVoice(it.name, it.label ?: it.name) }
    }

    /** Voices of [enginePackage] (blank = system default), sorted by language then name. */
    suspend fun voices(enginePackage: String): List<SpeechVoice> {
        val engine = obtain(enginePackage)
        val voices = withContext(Dispatchers.Main) { runCatching { engine.voices }.getOrNull().orEmpty() }
        return voices
            .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
            .map { voice ->
                val network = if (voice.isNetworkConnectionRequired) " · network" else ""
                SpeechVoice(voice.name, "${voice.locale.displayName} — ${voice.name}$network")
            }
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
                instance = TextToSpeech(appContext, { status ->
                    val ready = instance
                    if (status == TextToSpeech.SUCCESS && ready != null) {
                        if (cont.isActive) cont.resume(ready)
                    } else {
                        runCatching { ready?.shutdown() }
                        if (cont.isActive) {
                            cont.resumeWithException(SpeechEngineException("Android TTS engine is not available"))
                        }
                    }
                }, wanted)
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
