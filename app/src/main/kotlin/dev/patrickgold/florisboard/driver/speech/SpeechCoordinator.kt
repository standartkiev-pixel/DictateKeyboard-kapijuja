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
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.android.stringRes

/**
 * The one place that plays speech, whatever the engine (Driver Assistant, issue #7).
 *
 * - At most one reading at a time: a new [speak] or [stop] cancels the current one at once,
 *   including its network request, and a callback from a cancelled reading never restarts audio.
 * - Dictation wins: the moment the microphone starts recording, reading stops, so speech is never
 *   recorded into a transcript.
 * - Takes transient audio focus with ducking, so music lowers instead of pausing; a phone call or
 *   navigation prompt that takes focus stops the reading.
 * - Cloud audio is played gaplessly through [PcmStreamPlayer]: text is split into pieces, the first
 *   one short, and the next piece is requested while the current one plays — never more than one
 *   piece ahead, so a stop does not leave paid audio generating for text nobody will hear.
 *
 * Later stages (auto-read with countdown) call the same [speak]; nothing here is clipboard-specific.
 */
object SpeechCoordinator {
    private const val TAG = "KapijujaSpeech"

    /** Upper bound for one cloud reading, to keep a mistaken tap on a huge clip from costing much. */
    const val MAX_CLOUD_CHARS = 8_000
    const val MAX_LOCAL_CHARS = 60_000

    sealed interface State {
        data object Idle : State
        /** Waiting for the engine / first audio for the reading identified by [key]. */
        data class Preparing(val key: String) : State
        data class Speaking(val key: String) : State
    }

    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var currentJob: Job? = null
    private var generation = 0L
    private var android: AndroidSpeechEngine? = null
    private var dictationWatch: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    /** Key of the reading being prepared or spoken, or null when idle. */
    fun activeKey(): String? = when (val s = _state.value) {
        is State.Preparing -> s.key
        is State.Speaking -> s.key
        State.Idle -> null
    }

    fun isActive(key: String): Boolean = when (val s = _state.value) {
        is State.Preparing -> s.key == key
        is State.Speaking -> s.key == key
        State.Idle -> false
    }

    /** Starts reading [text], or stops if [key] is the reading in progress (the speaker toggles). */
    fun toggle(context: Context, text: String, key: String) {
        if (isActive(key)) stop() else speak(context, text, key)
    }

    fun speak(context: Context, text: String, key: String) {
        val appContext = context.applicationContext
        watchDictation()
        stop()
        val myGeneration = ++generation
        _state.value = State.Preparing(key)
        currentJob = scope.launch {
            val settings = withContext(Dispatchers.IO) { SpeechSettings.from(prefs) }
            requestFocus(appContext)
            try {
                read(appContext, text, settings) {
                    if (generation == myGeneration) _state.value = State.Speaking(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "reading failed: engine=${settings.engine} error=${e.javaClass.simpleName}")
                val message = (e as? SpeechEngineException)?.message ?: e.javaClass.simpleName
                appContext.showShortToast(R.string.speech__error_toast, "message" to message)
            } finally {
                if (generation == myGeneration) {
                    abandonFocus()
                    _state.value = State.Idle
                }
            }
        }
    }

    fun stop() {
        generation++
        currentJob?.cancel()
        currentJob = null
        android?.stop()
        abandonFocus()
        _state.value = State.Idle
    }

    /** Used by settings when the Android engine choice changes or the screen closes. */
    fun androidEngine(context: Context): AndroidSpeechEngine =
        android ?: AndroidSpeechEngine(context).also { android = it }

    private suspend fun read(context: Context, text: String, settings: SpeechSettings, onStarted: () -> Unit) {
        if (!settings.engine.isCloud) {
            androidEngine(context).speak(text.take(MAX_LOCAL_CHARS), settings, onStarted)
            return
        }
        val source = sourceFor(settings.engine)
        if (!source.isConfigured(settings)) {
            if (settings.fallbackToAndroid) {
                context.showShortToast(R.string.speech__fallback_not_configured_toast, "engine" to settings.engine.name)
                androidEngine(context).speak(text.take(MAX_LOCAL_CHARS), settings, onStarted)
                return
            }
            throw SpeechEngineException(context.stringRes(R.string.speech__not_configured_error, "engine" to settings.engine.name))
        }
        var started = false
        try {
            playCloud(source, text.take(MAX_CLOUD_CHARS), settings) {
                started = true
                onStarted()
            }
        } catch (e: SpeechEngineException) {
            // Fall back only when nothing was heard yet: repeating a half-read text on another
            // engine is confusing, and retrying the cloud would risk paying twice.
            if (!settings.fallbackToAndroid || started) throw e
            context.showShortToast(R.string.speech__fallback_error_toast, "message" to e.message)
            androidEngine(context).speak(text.take(MAX_LOCAL_CHARS), settings, onStarted)
        }
    }

    internal fun sourceFor(engine: SpeechEngineId): CloudSpeechSource = when (engine) {
        SpeechEngineId.OPENAI -> OpenAiRealtimeSpeechSource
        SpeechEngineId.AZURE -> AzureSpeechSource
        SpeechEngineId.GOOGLE -> GeminiSpeechSource
        SpeechEngineId.XAI -> XaiSpeechSource
        SpeechEngineId.ANDROID -> error("Android is not a cloud source")
    }

    private sealed interface Piece {
        class Start(val index: Int) : Piece
        class Audio(val pcm: ByteArray) : Piece
    }

    /**
     * Producer/consumer over one channel: the producer synthesizes piece after piece and pushes PCM as
     * it streams in; the consumer writes it into a single [PcmStreamPlayer]. The producer waits before
     * piece N+1 until the player has reached piece N, which is the "at most one piece ahead" rule.
     */
    private suspend fun playCloud(
        source: CloudSpeechSource,
        text: String,
        settings: SpeechSettings,
        onStarted: () -> Unit,
    ) = coroutineScope {
        val pieces = SpeechTextChunker.split(text)
        if (pieces.isEmpty()) return@coroutineScope
        val channel = Channel<Piece>(Channel.UNLIMITED)
        val playing = MutableStateFlow(-1)
        val producer = launch(Dispatchers.IO) {
            try {
                pieces.forEachIndexed { index, piece ->
                    if (index > 0) playing.first { it >= index - 1 }
                    channel.send(Piece.Start(index))
                    source.synthesize(piece, settings) { pcm -> channel.send(Piece.Audio(pcm)) }
                }
                channel.close()
            } catch (e: Throwable) {
                channel.close(e)
                throw e
            }
        }
        withContext(Dispatchers.IO) {
            val player = PcmStreamPlayer(boostDb = settings.boostDb)
            var announced = false
            try {
                for (piece in channel) {
                    when (piece) {
                        is Piece.Start -> playing.value = piece.index
                        is Piece.Audio -> {
                            player.write(piece.pcm)
                            if (!announced) {
                                announced = true
                                withContext(Dispatchers.Main) { onStarted() }
                            }
                        }
                    }
                }
                player.drain()
            } finally {
                player.release()
            }
        }
        producer.join()
    }

    private fun watchDictation() {
        if (dictationWatch != null) return
        dictationWatch = scope.launch {
            DictateController.state.collect { uiState ->
                if (uiState is DictateController.UiState.Recording && _state.value != State.Idle) stop()
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> scope.launch { stop() }
        }
    }

    private var focusManager: AudioManager? = null

    private fun requestFocus(context: Context) {
        abandonFocus()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(SpeechAudio.attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusManager = am
        focusRequest = request
        am.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        runCatching { focusManager?.abandonAudioFocusRequest(request) }
        focusRequest = null
        focusManager = null
    }
}
