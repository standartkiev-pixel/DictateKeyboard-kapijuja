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
 * Owns the temporary audio references and background jobs for one long-form session.
 *
 * Segment callbacks can complete off the main thread, so every collection access is synchronized.
 * Taking audio transfers deletion responsibility to the caller, which may first merge the files into a
 * history or rescue WAV. Clearing references alone never deletes files.
 */
internal class LongFormSessionResources {
    private val audioByIndex = HashMap<Int, File>()
    private val jobs = mutableSetOf<Job>()

    /** Keeps one non-empty segment file under this session's ownership. */
    @Synchronized
    fun trackAudio(index: Int, file: File) {
        if (file.exists() && file.length() > 0L) audioByIndex[index] = file
    }

    @Synchronized
    fun containsAudio(file: File): Boolean = audioByIndex.values.any { it == file }

    /**
     * Transfers all usable audio to the caller in speaking order and forgets every tracked reference.
     */
    @Synchronized
    fun takeOrderedAudio(): List<File> {
        val ordered = audioByIndex.toSortedMap().values
            .filter { it.exists() && it.length() > 0L }
            .toList()
        audioByIndex.clear()
        return ordered
    }

    /** Deletes all tracked audio when the entire recording is intentionally discarded. */
    fun discardAudio() {
        val files = synchronized(this) {
            audioByIndex.values.toList().also { audioByIndex.clear() }
        }
        files.forEach { runCatching { it.delete() } }
    }

    /** Forgets stale references at session setup without deleting caller-owned files. */
    @Synchronized
    fun clearAudioReferences() {
        audioByIndex.clear()
    }

    /** Tracks a job until completion, including cancellation and provider failure. */
    @Synchronized
    fun trackJob(job: Job) {
        jobs.add(job)
        job.invokeOnCompletion {
            synchronized(this) { jobs.remove(job) }
        }
    }

    /** Cancels one stable snapshot so completion callbacks cannot mutate an active iteration. */
    fun cancelJobs() {
        val active = synchronized(this) {
            jobs.toList().also { jobs.clear() }
        }
        active.forEach { it.cancel() }
    }

    @get:Synchronized
    internal val trackedJobCount: Int
        get() = jobs.size
}
