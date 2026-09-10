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

import kotlinx.coroutines.Job

/** Remaining no-progress budget, sampled by the same watchdog that cancels the request. */
data class TranscriptionCountdown(val remainingMs: Long, val timeoutMs: Long) {
    val secondsRemaining: Long get() = (remainingMs + 999L) / 1_000L
    val fractionRemaining: Float get() = (remainingMs.toFloat() / timeoutMs).coerceIn(0f, 1f)

    companion object {
        fun afterIdle(timeoutMs: Long, idleMs: Long): TranscriptionCountdown {
            require(timeoutMs > 0L)
            return TranscriptionCountdown(timeoutMs - idleMs.coerceIn(0L, timeoutMs), timeoutMs)
        }
    }
}

/** Native workers can outlive cancellation; their late heartbeat must not extend a new request. */
internal fun activeTranscriptionProgress(job: Job, progress: () -> Unit): () -> Unit = {
    if (job.isActive) progress()
}
