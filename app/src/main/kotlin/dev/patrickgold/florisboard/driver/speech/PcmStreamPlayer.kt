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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

/**
 * Plays 16-bit mono PCM as it arrives, through one [AudioTrack] for the whole text.
 *
 * This is the part that keeps cloud speech from stuttering. Kapijuja Reader plays every synthesized
 * piece as its own file in a fresh MediaPlayer, so each boundary costs a prepare/start and a gap. Here
 * all pieces are written back to back into the same stream, so a boundary is inaudible as long as the
 * next piece's audio arrives before the buffer runs dry. Playback starts only once [prebufferMs] of
 * audio is queued, so a slow first network read does not turn into a crackle in the first second.
 *
 * One coroutine writes, and the same coroutine calls [drain] and [release]; only [pause] and
 * [resume] may be called from another thread.
 */
class PcmStreamPlayer(
    private val sampleRate: Int = PcmAudio.SAMPLE_RATE,
    private val prebufferMs: Int = 350,
    boostDb: Int = 0,
) {
    private val track: AudioTrack
    private val loudness: LoudnessEnhancer?
    private val prebuffer = ByteArrayOutputStream()
    private val prebufferBytes = PcmAudio.bytesForMillis(prebufferMs, sampleRate)
    private val playLock = Any()
    private var started = false
    private var paused = false
    private var framesWritten = 0L

    init {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(PcmAudio.bytesForMillis(100, sampleRate))
        // About 1.5 s of room: large enough to ride out a slow network read, small enough that a stop
        // takes effect immediately (stop() + flush() drop whatever is still queued).
        val bufferBytes = maxOf(minBuffer * 4, PcmAudio.bytesForMillis(1500, sampleRate))
        track = AudioTrack.Builder()
            .setAudioAttributes(SpeechAudio.attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        loudness = SpeechAudio.loudness(track.audioSessionId, boostDb)
    }

    /** Queues [pcm] (whole 16-bit frames). Blocks while the track buffer is full. */
    suspend fun write(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        if (!started) {
            prebuffer.write(pcm)
            if (prebuffer.size() >= prebufferBytes) startWithPrebuffer()
            return
        }
        writeFully(pcm)
    }

    /** Plays out everything queued and returns once the last frame has been heard. */
    suspend fun drain() {
        if (!started) {
            if (prebuffer.size() == 0) return
            startWithPrebuffer()
        }
        // Pad a little silence so the device does not clip the very last syllable on some HALs.
        writeFully(ByteArray(PcmAudio.bytesForMillis(120, sampleRate)))
        val target = framesWritten
        val expectedMs = (target - playedFrames()).coerceAtLeast(0) * 1000L / sampleRate
        var deadline = SystemClock.elapsedRealtime() + expectedMs + 2_000L
        while (playedFrames() < target) {
            currentCoroutineContext().ensureActive()
            // A paused stream is not a stalled one: the time spent paused does not count.
            if (isPaused()) deadline += 40 else if (SystemClock.elapsedRealtime() >= deadline) break
            delay(40)
        }
    }

    /** Holds playback where it is; queued audio is kept. Safe before the first sound. */
    fun pause() = synchronized(playLock) {
        paused = true
        if (started) runCatching { track.pause() }
    }

    /** Continues after [pause] from the exact sample it stopped at. */
    fun resume() = synchronized(playLock) {
        paused = false
        if (started) runCatching { track.play() }
    }

    private fun isPaused(): Boolean = synchronized(playLock) { paused }

    fun release() {
        runCatching {
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                track.pause()
                track.flush()
                track.stop()
            }
        }
        runCatching { loudness?.release() }
        runCatching { track.release() }
    }

    private suspend fun startWithPrebuffer() {
        val bytes = prebuffer.toByteArray()
        prebuffer.reset()
        synchronized(playLock) {
            started = true
            if (!paused) track.play()
        }
        writeFully(bytes)
    }

    private suspend fun writeFully(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            currentCoroutineContext().ensureActive()
            // Returns early (possibly 0) while the track is paused; then wait and try again.
            val n = track.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_BLOCKING)
            if (n < 0) error("AudioTrack write failed: $n")
            if (n == 0) delay(20)
            offset += n
        }
        framesWritten += bytes.size / PcmAudio.BYTES_PER_FRAME
    }

    /** Frames actually played. The head position is an unsigned 32-bit counter. */
    private fun playedFrames(): Long = track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
}

/**
 * Audio attributes shared by every engine. Media usage on purpose: it follows the media volume the
 * user sets with the volume keys. The assistant usage it replaced is mapped to a separate, often
 * much quieter volume curve on some phones (reported as "the voice is quiet" on a Samsung).
 */
object SpeechAudio {
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * Adds [boostDb] of gain with the platform limiter to the audio session [sessionId], or returns
     * null when the device has no LoudnessEnhancer (the speech then simply plays unboosted).
     */
    fun loudness(sessionId: Int, boostDb: Int): LoudnessEnhancer? {
        if (boostDb <= 0 || sessionId <= 0) return null
        return runCatching {
            LoudnessEnhancer(sessionId).apply {
                setTargetGain(boostDb.coerceAtMost(MAX_BOOST_DB) * 100)
                enabled = true
            }
        }.getOrNull()
    }

    const val MAX_BOOST_DB = 15
}
