/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the single wall-clock deadline for one batch transcription request.
 *
 * Provider progress and internal retries cannot renew this deadline. Starting a replacement request
 * invalidates the previous generation, so delayed cleanup from the old coroutine cannot clear the new
 * countdown. The owner decides how an expired request is cancelled and how its audio is retained.
 */
internal class TranscriptionWatchdog(
    private val scope: CoroutineScope,
    private val clockMs: () -> Long,
    private val pollMs: Long = DEFAULT_POLL_MS,
) {
    private val _countdown = MutableStateFlow<TranscriptionCountdown?>(null)
    val countdown: StateFlow<TranscriptionCountdown?> = _countdown.asStateFlow()

    private var job: Job? = null
    private var generation = 0L

    /** Starts a new deadline and returns its generation for diagnostic logging. */
    fun start(
        timeoutMs: Long,
        isRequestActive: () -> Boolean,
        onExpired: (elapsedMs: Long, generation: Long) -> Unit,
    ): Long {
        require(timeoutMs > 0L)
        stop()

        val startedAtMs = clockMs()
        val currentGeneration = ++generation
        _countdown.value = TranscriptionCountdown(timeoutMs, timeoutMs)
        job = scope.launch {
            try {
                while (true) {
                    delay(pollMs)
                    if (!isRequestActive()) return@launch

                    val elapsedMs = (clockMs() - startedAtMs).coerceAtLeast(0L)
                    val snapshot = TranscriptionCountdown.afterElapsed(timeoutMs, elapsedMs)
                    _countdown.value = snapshot
                    if (snapshot.remainingMs == 0L) {
                        onExpired(elapsedMs, currentGeneration)
                        return@launch
                    }
                }
            } finally {
                if (generation == currentGeneration) {
                    job = null
                    _countdown.value = null
                }
            }
        }
        return currentGeneration
    }

    /** Invalidates and clears the current deadline without cancelling the provider request. */
    fun stop() {
        generation++
        job?.cancel()
        job = null
        _countdown.value = null
    }

    private companion object {
        /** One-second sampling is cheap and keeps the visible seconds aligned with expiry. */
        const val DEFAULT_POLL_MS = 1_000L
    }
}
