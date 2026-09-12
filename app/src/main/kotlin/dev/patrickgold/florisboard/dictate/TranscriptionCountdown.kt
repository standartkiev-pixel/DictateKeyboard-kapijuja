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

/** Remaining wall-clock budget, sampled by the same watchdog that cancels the request. */
data class TranscriptionCountdown(val remainingMs: Long, val timeoutMs: Long) {
    val secondsRemaining: Long get() = (remainingMs + 999L) / 1_000L
    val fractionRemaining: Float get() = (remainingMs.toFloat() / timeoutMs).coerceIn(0f, 1f)

    companion object {
        fun afterElapsed(timeoutMs: Long, elapsedMs: Long): TranscriptionCountdown {
            require(timeoutMs > 0L)
            return TranscriptionCountdown(timeoutMs - elapsedMs.coerceIn(0L, timeoutMs), timeoutMs)
        }
    }
}
