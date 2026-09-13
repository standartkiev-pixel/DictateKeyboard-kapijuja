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

import dev.patrickgold.florisboard.dictate.audio.AudioDecode
import dev.patrickgold.florisboard.dictate.audio.AudioWav
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LongFormAudioAssemblyTest {
    @Test fun `consume merges ordered segments and deletes every source`() {
        val directory = Files.createTempDirectory("long-form-assembly-").toFile()
        try {
            val first = wav(directory, "first.wav", floatArrayOf(0.1f, 0.2f))
            val second = wav(directory, "second.wav", floatArrayOf(0.3f))
            val destination = File(directory, "rescue.wav")

            val merged = LongFormAudioAssembly.consume(listOf(first, second), destination)

            assertEquals(destination, merged)
            assertEquals(3, AudioDecode.decodeToMono16k(destination).size)
            assertFalse(first.exists())
            assertFalse(second.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `no destination discards sources without producing output`() {
        val directory = Files.createTempDirectory("long-form-no-history-").toFile()
        try {
            val source = wav(directory, "segment.wav", floatArrayOf(0.1f))

            assertNull(LongFormAudioAssembly.consume(listOf(source), destination = null))
            assertFalse(source.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `invalid segments return no rescue and leave no partial destination`() {
        val directory = Files.createTempDirectory("long-form-invalid-").toFile()
        try {
            val invalid = File(directory, "invalid.wav").apply { writeText("not a wav") }
            val destination = File(directory, "rescue.wav").apply { writeText("stale") }

            assertNull(LongFormAudioAssembly.consume(listOf(invalid), destination))
            assertFalse(invalid.exists())
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `filesystem failure becomes a terminal null and cleans every file`() {
        val directory = Files.createTempDirectory("long-form-failure-").toFile()
        try {
            val source = wav(directory, "segment.wav", floatArrayOf(0.1f))
            val destination = File(directory, "rescue.wav")

            val rescue = LongFormAudioAssembly.consume(listOf(source), destination) { _, output ->
                output.writeText("partial")
                error("simulated filesystem failure")
            }

            assertNull(rescue)
            assertFalse(source.exists())
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun wav(directory: File, name: String, samples: FloatArray): File =
        File(directory, name).also { output ->
            assertTrue(AudioWav.write(samples, AudioDecode.TARGET_SAMPLE_RATE, output))
        }
}
