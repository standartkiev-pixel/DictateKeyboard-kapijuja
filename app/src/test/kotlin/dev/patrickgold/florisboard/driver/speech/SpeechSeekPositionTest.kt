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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechSeekPositionTest {
    @Test
    fun `seek from the middle starts at the beginning of that sentence`() {
        val text = "First sentence. Second sentence is longer. Third sentence."
        val offset = SpeechSeekPosition.offset(text, 0.50f)
        assertEquals(text.indexOf("Second sentence"), offset)
    }

    @Test
    fun `seek from a text without punctuation stays near requested position`() {
        val text = "one two three four five six seven eight nine ten"
        val offset = SpeechSeekPosition.offset(text, 0.7f)
        assertTrue(offset in (text.length * 0.7f).toInt() - 10..(text.length * 0.7f).toInt())
        assertTrue(offset == 0 || text[offset - 1].isWhitespace())
    }

    @Test
    fun `seek fractions are clamped at start and end`() {
        val text = "Some words here"
        assertEquals(0, SpeechSeekPosition.offset(text, -10f))
        assertEquals(text.length, SpeechSeekPosition.offset(text, 10f))
        assertEquals(0, SpeechSeekPosition.offset("", 0.5f))
    }

    @Test
    fun `duration estimate respects voice speed and nonzero duration`() {
        val text = "A paragraph with enough words to be read for a while. ".repeat(30)
        assertTrue(SpeechSeekPosition.estimatedDurationMs(text, 2f) <
            SpeechSeekPosition.estimatedDurationMs(text, 1f))
        assertTrue(SpeechSeekPosition.estimatedDurationMs("", 1f) >= 3000L)
    }
}
