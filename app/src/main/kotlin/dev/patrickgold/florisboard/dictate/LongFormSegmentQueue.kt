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

/**
 * Tracks long-form segment indices and releases completed text strictly in speaking order.
 *
 * Provider jobs may finish in any order. A later result remains buffered until every earlier index has
 * landed, while empty/silent segments still advance the sequence. This class owns no jobs or files and
 * must be called under the controller's segment mutex.
 */
internal class LongFormSegmentQueue {
    data class Drain(
        val readyText: List<String>,
        val inFlightCount: Int,
        val shouldFinish: Boolean,
    )

    private var nextIndex = 0
    private var commitIndex = 0
    private val results = HashMap<Int, String>()
    private var stopped = false

    var inFlightCount = 0
        private set

    /** Reserves one transcribed segment in cut order. */
    fun reserve(): Int {
        inFlightCount++
        return nextIndex++
    }

    /** Reserves the final segment and marks the queue ready to finish after its complete drain. */
    fun reserveFinal(): Int {
        stopped = true
        return reserve()
    }

    /** Reserves an ordered audio-only tail used to assemble a cancellation rescue WAV. */
    fun reserveRescueTail(): Int = nextIndex++

    /** Buffers one result and returns every newly contiguous segment, already whitespace-trimmed. */
    fun complete(index: Int, text: String): Drain {
        results[index] = text
        val ready = buildList {
            while (results.containsKey(commitIndex)) {
                add(results.remove(commitIndex)!!.trim())
                commitIndex++
            }
        }
        inFlightCount--
        return Drain(
            readyText = ready,
            inFlightCount = inFlightCount.coerceAtLeast(0),
            shouldFinish = stopped && inFlightCount <= 0,
        )
    }

    /** Clears all per-session state; source audio remains owned and cleaned up by the controller. */
    fun reset() {
        nextIndex = 0
        commitIndex = 0
        results.clear()
        stopped = false
        inFlightCount = 0
    }
}
