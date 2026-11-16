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
import android.os.SystemClock
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 * - [pause] holds the reading where it is and [resume] continues it: cloud audio from the exact
 *   sample, the Android engine from the start of the sentence it was in (it has no pause of its own).
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
        data class Paused(val key: String) : State
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

    /** True while the current reading is held by [pause]; the engines watch it. */
    private val pauseRequested = MutableStateFlow(false)

    /** Whether the current reading has produced sound yet (decides the state after [resume]). */
    private var audible = false

    /** The current source text, not its synthesized audio; needed for approximate sentence seeking. */
    private data class Reading(
        val context: Context,
        val text: String,
        val key: String,
        val adjust: (SpeechSettings) -> SpeechSettings,
    )
    private var reading: Reading? = null

    private val _progress = MutableStateFlow(0f)
    /** Fraction of the text already spoken (best-effort, updated by TTS callbacks). */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _estimatedDurationMs = MutableStateFlow(3_000L)
    /** Estimated duration, not measured synthesized audio length. */
    val estimatedDurationMs: StateFlow<Long> = _estimatedDurationMs.asStateFlow()


    /** Key of the reading being prepared, spoken or paused, or null when idle. */
    fun activeKey(): String? = keyOf(_state.value)

    fun keyOf(state: State): String? = when (state) {
        is State.Preparing -> state.key
        is State.Speaking -> state.key
        is State.Paused -> state.key
        State.Idle -> null
    }

    fun isActive(key: String): Boolean = activeKey() == key

    val isPaused: Boolean get() = _state.value is State.Paused

    /** Starts reading [text], or stops if [key] is the reading in progress (the speaker toggles). */
    fun toggle(context: Context, text: String, key: String) {
        if (isActive(key)) stop() else speak(context, text, key)
    }

    /**
     * Reads [text] aloud. [adjust] lets a caller try something out without saving it first, such as
     * one voice from the list in settings.
     */
    fun speak(
        context: Context,
        text: String,
        key: String,
        adjust: (SpeechSettings) -> SpeechSettings = { it },
    ) = begin(context.applicationContext, text, key, adjust, startAt = 0, retainingKey = false)

    /** Seek the currently playing text to a sentence/word boundary near [fraction]. */
    fun seekToFraction(fraction: Float) {
        val session = reading ?: return
        if (activeKey() != session.key) return
        val startAt = SpeechSeekPosition.offset(session.text, fraction)
        begin(session.context, session.text, session.key, session.adjust, startAt, retainingKey = true)
    }

    private fun begin(
        context: Context,
        text: String,
        key: String,
        adjust: (SpeechSettings) -> SpeechSettings,
        startAt: Int,
        retainingKey: Boolean,
    ) {
        watchDictation()
        if (retainingKey) {
            // Do not broadcast Idle when seeking: AutoReadController tracks this key and would
            // otherwise mistake the temporary restart for the end of the whole reading.
            generation++
            currentJob?.cancel()
            currentJob = null
            android?.stop()
            abandonFocus()
            pauseRequested.value = false
            audible = false
        } else {
            stop()
        }
        val cappedText = text.take(MAX_LOCAL_CHARS)
        val myGeneration = ++generation
        reading = Reading(context, cappedText, key, adjust)
        _progress.value = if (cappedText.isEmpty()) 0f else startAt.toFloat() / cappedText.length
        _estimatedDurationMs.value = SpeechSeekPosition.estimatedDurationMs(cappedText, 1f)
        _state.value = State.Preparing(key)

        currentJob = scope.launch {
            var progressClock: Job? = null
            try {
                val settings = adjust(withContext(Dispatchers.IO) { SpeechSettings.from(prefs) })
                val playable = cappedText.take(if (settings.engine.isCloud) MAX_CLOUD_CHARS else MAX_LOCAL_CHARS)
                if (generation != myGeneration) return@launch
                reading = Reading(context, playable, key, adjust)
                val offset = startAt.coerceIn(0, playable.length)
                _estimatedDurationMs.value = SpeechSeekPosition.estimatedDurationMs(playable, settings.speed)
                _progress.value = if (playable.isEmpty()) 0f else offset.toFloat() / playable.length
                requestFocus(context)
                progressClock = launch {
                    var last = SystemClock.elapsedRealtime()
                    while (isActive) {
                        delay(500L)
                        val now = SystemClock.elapsedRealtime()
                        if (generation == myGeneration && _state.value is State.Speaking) {
                            val advance = (now - last).toFloat() / _estimatedDurationMs.value.coerceAtLeast(1L)
                            _progress.value = (_progress.value + advance).coerceAtMost(0.995f)
                        }
                        last = now
                    }
                }
                read(context, playable.substring(offset), settings, onStarted = {
                    if (generation == myGeneration) {
                        audible = true
                        if (_state.value is State.Preparing) _state.value = State.Speaking(key)
                    }
                }, onProgress = { spokenInRemainder ->
                    if (generation == myGeneration && playable.isNotEmpty()) {
                        val exact = ((offset + spokenInRemainder).toFloat() / playable.length).coerceIn(0f, 0.995f)
                        if (exact > _progress.value) _progress.value = exact
                    }
                })
                if (generation == myGeneration) _progress.value = 1f
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "reading failed: error=${e.javaClass.simpleName}")
                val message = (e as? SpeechEngineException)?.message ?: e.javaClass.simpleName
                context.showShortToast(R.string.speech__error_toast, "message" to message)
            } finally {
                progressClock?.cancel()
                if (generation == myGeneration) {
                    abandonFocus()
                    pauseRequested.value = false
                    audible = false
                    reading = null
                    _state.value = State.Idle
                }
            }
        }
    }

    fun stop() {
        generation++
        currentJob?.cancel()
        currentJob = null
        reading = null
        android?.stop()
        abandonFocus()
        pauseRequested.value = false
        audible = false
        _state.value = State.Idle
    }

    /** Holds the current reading; no-op when idle or already paused. Audio focus is given back. */
    fun pause() {
        val key = activeKey() ?: return
        if (_state.value is State.Paused) return
        pauseRequested.value = true
        abandonFocus()
        _state.value = State.Paused(key)
    }

    /** Continues a reading held by [pause]. */
    fun resume(context: Context) {
        val paused = _state.value as? State.Paused ?: return
        requestFocus(context.applicationContext)
        _state.value = if (audible) State.Speaking(paused.key) else State.Preparing(paused.key)
        pauseRequested.value = false
    }

    fun togglePause(context: Context) {
        if (_state.value is State.Paused) resume(context) else pause()
    }

    /** Used by settings when the Android engine choice changes or the screen closes. */
    fun androidEngine(context: Context): AndroidSpeechEngine =
        android ?: AndroidSpeechEngine(context).also { android = it }

    private suspend fun read(context: Context, text: String, settings: SpeechSettings, onStarted: () -> Unit, onProgress: (Int) -> Unit) {
        if (!settings.engine.isCloud) {
            androidEngine(context).speak(text.take(MAX_LOCAL_CHARS), settings, pauseRequested, onStarted, onProgress)
            return
        }
        val source = sourceFor(settings.engine)
        if (!source.isConfigured(settings)) {
            if (settings.fallbackToAndroid) {
                context.showShortToast(R.string.speech__fallback_not_configured_toast, "engine" to settings.engine.name)
                androidEngine(context).speak(text.take(MAX_LOCAL_CHARS), settings, pauseRequested, onStarted, onProgress)
                return
            }
            throw SpeechEngineException(context.stringRes(R.string.speech__not_configured_error, "engine" to settings.engine.name))
        }
        var started = false
        try {
            playCloud(source, text.take(MAX_CLOUD_CHARS), settings, onStarted = {
                started = true
                onStarted()
            }, onProgress = onProgress)
        } catch (e: SpeechEngineException) {
            // Fall back only when nothing was heard yet: repeating a half-read text on another
            // engine is confusing, and retrying the cloud would risk paying twice.
            if (!settings.fallbackToAndroid || started) throw e
            context.showShortToast(R.string.speech__fallback_error_toast, "message" to e.message)
            androidEngine(context).speak(text.take(MAX_LOCAL_CHARS), settings, pauseRequested, onStarted, onProgress)
        }
    }

    internal fun sourceFor(engine: SpeechEngineId): CloudSpeechSource = when (engine) {
        SpeechEngineId.OPENAI -> OpenAiRealtimeSpeechSource
        SpeechEngineId.AZURE -> AzureSpeechSource
        SpeechEngineId.GOOGLE -> GeminiSpeechSource
        SpeechEngineId.XAI -> XaiSpeechSource
        SpeechEngineId.ELEVENLABS -> ElevenLabsSpeechSource
        SpeechEngineId.INWORLD -> InworldSpeechSource
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
        onProgress: (Int) -> Unit,
    ) = coroutineScope {
        val pieces = SpeechTextChunker.split(text)
        if (pieces.isEmpty()) return@coroutineScope
        val chunkOffsets = buildList {
            var count = 0
            for (piece in pieces) {
                add(count)
                count += piece.length
            }
        }
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
            val pauseWatch = launch { pauseRequested.collect { if (it) player.pause() else player.resume() } }
            var announced = false
            try {
                for (piece in channel) {
                    when (piece) {
                        is Piece.Start -> {
                            playing.value = piece.index
                            onProgress(chunkOffsets[piece.index])
                        }
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
                pauseWatch.cancel()
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
