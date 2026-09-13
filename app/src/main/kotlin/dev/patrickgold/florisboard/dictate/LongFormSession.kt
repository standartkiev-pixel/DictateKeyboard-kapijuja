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
import java.io.File

/**
 * Owns the state machine, ordered queue, and temporary resources for one long-form session.
 *
 * Queue operations still run under the controller's segment mutex. The cancellation guard deliberately
 * survives [resetRuntimeState] while a rescue WAV is assembled, preventing a second Stop or watchdog
 * callback from starting another terminal path over the same recording.
 */
internal class LongFormSession(
    private val queue: LongFormSegmentQueue = LongFormSegmentQueue(),
    private val resources: LongFormSessionResources = LongFormSessionResources(),
) {
    var isActive: Boolean = false
        private set

    @Volatile
    var cancellationPending: Boolean = false
        private set

    var recordedSeconds: Long = 0L
        private set

    var keepAudio: Boolean = false
        private set

    val inFlightCount: Int
        get() = queue.inFlightCount

    /** Starts a clean session without touching files already transferred to another owner. */
    @Synchronized
    fun begin(keepAudio: Boolean) {
        isActive = true
        queue.reset()
        resources.clearAudioReferences()
        cancellationPending = false
        recordedSeconds = 0L
        this.keepAudio = keepAudio
    }

    fun rememberRecordedSeconds(seconds: Long) {
        recordedSeconds = seconds
    }

    /** Returns false when another terminal path is already assembling the rescue recording. */
    @Synchronized
    fun beginCancellation(): Boolean {
        if (cancellationPending) return false
        cancellationPending = true
        return true
    }

    @Synchronized
    fun finishCancellation() {
        cancellationPending = false
    }

    /**
     * Leaves recording/transcription mode but preserves the cancellation guard until rescue completes.
     */
    fun resetRuntimeState() {
        isActive = false
        queue.reset()
        resources.clearAudioReferences()
    }

    fun reserveSegment(): Int = queue.reserve()

    fun reserveFinalSegment(): Int = queue.reserveFinal()

    fun reserveRescueTail(): Int = queue.reserveRescueTail()

    fun completeSegment(index: Int, text: String): LongFormSegmentQueue.Drain =
        queue.complete(index, text)

    fun trackAudio(index: Int, file: File) = resources.trackAudio(index, file)

    fun containsAudio(file: File): Boolean = resources.containsAudio(file)

    fun takeOrderedAudio(): List<File> = resources.takeOrderedAudio()

    fun discardAudio() = resources.discardAudio()

    fun trackJob(job: Job) = resources.trackJob(job)

    fun cancelJobs() = resources.cancelJobs()
}
