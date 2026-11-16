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

/** Text is not a pre-rendered audio file. Seek is approximate, to a word/sentence boundary. */
internal object SpeechSeekPosition {
    fun offset(text: String, fraction: Float): Int {
        if (text.isEmpty()) return 0
        val target = (text.length * fraction.coerceIn(0f, 1f)).toInt().coerceIn(0, text.length)
        if (target == 0 || target == text.length) return target
        // Prefer the beginning of the sentence containing the requested location. Never
        // restart hundreds of words earlier when punctuation is scarce.
        val punctuation = ".!?\n。！？"
        val sentenceBoundary = (target - 1 downTo (target - 240).coerceAtLeast(0))
            .firstOrNull { text[it] in punctuation }
        if (sentenceBoundary != null) {
            var at = sentenceBoundary + 1
            while (at < target && text[at].isWhitespace()) at++
            return at
        }
        // In a long unpunctuated text, begin at the nearest previous word boundary.
        val wordBoundary = (target - 1 downTo (target - 80).coerceAtLeast(0))
            .firstOrNull { text[it].isWhitespace() }
        return wordBoundary?.plus(1) ?: target
    }

    /** Relative skip in the approximate playback timeline, constrained to the text bounds. */
    fun shiftedFraction(progress: Float, deltaMillis: Long, durationMillis: Long): Float {
        if (durationMillis <= 0L) return progress.coerceIn(0f, 1f)
        val fraction = progress.coerceIn(0f, 1f) +
            deltaMillis.toDouble() / durationMillis.toDouble()
        return fraction.coerceIn(0.0, 1.0).toFloat()
    }

    /** Estimate only: actual TTS duration depends on language, voice and network engine. */
    fun estimatedDurationMs(text: String, speed: Float): Long {
        val charsPerSecond = 13f * speed.coerceIn(0.25f, 4f)
        return ((text.length / charsPerSecond) * 1000f).toLong().coerceIn(3_000L, 10_800_000L)
    }
}
