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

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LongFormSessionResourcesTest {
    @Test fun `audio transfers in speaking order and ignores unusable files`() {
        val directory = createTempDirectory("long-form-resources-").toFile()
        try {
            val resources = LongFormSessionResources()
            val first = File(directory, "first.wav").apply { writeBytes(byteArrayOf(1)) }
            val second = File(directory, "second.wav").apply { writeBytes(byteArrayOf(2)) }
            val empty = File(directory, "empty.wav").apply { createNewFile() }

            resources.trackAudio(1, second)
            resources.trackAudio(2, empty)
            resources.trackAudio(0, first)

            assertEquals(listOf(first, second), resources.takeOrderedAudio())
            assertEquals(emptyList(), resources.takeOrderedAudio())
            assertTrue(first.exists())
            assertTrue(second.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `discard deletes tracked audio and clears ownership`() {
        val directory = createTempDirectory("long-form-discard-").toFile()
        try {
            val resources = LongFormSessionResources()
            val audio = File(directory, "segment.wav").apply { writeBytes(byteArrayOf(1)) }
            resources.trackAudio(0, audio)

            resources.discardAudio()

            assertFalse(audio.exists())
            assertEquals(emptyList(), resources.takeOrderedAudio())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `cancel jobs uses a stable snapshot and completion removes ownership`() = runTest {
        val resources = LongFormSessionResources()
        val first = launch { awaitCancellation() }
        val second = launch { awaitCancellation() }
        resources.trackJob(first)
        resources.trackJob(second)
        runCurrent()
        assertEquals(2, resources.trackedJobCount)

        resources.cancelJobs()
        runCurrent()

        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
        assertEquals(0, resources.trackedJobCount)
    }
}
