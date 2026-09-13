/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.audio

/**
 * Counts captured samples and emits one cut whenever a segment reaches its hard duration limit.
 *
 * Capture and VAD reset run on different threads, so both operations are synchronized. A cut consumes
 * exactly one limit interval; any frame remainder starts the next segment instead of being lost.
 */
internal class SegmentDurationPolicy(private val maxSamples: Long) {
    private var capturedSamples = 0L

    init {
        require(maxSamples > 0L)
    }

    @Synchronized
    fun onSamples(sampleCount: Int): Boolean {
        if (sampleCount <= 0) return false
        capturedSamples += sampleCount
        if (capturedSamples < maxSamples) return false
        capturedSamples %= maxSamples
        return true
    }

    @Synchronized
    fun reset() {
        capturedSamples = 0L
    }
}
