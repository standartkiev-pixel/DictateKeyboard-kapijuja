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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LongFormSegmentQueueTest {
    @Test fun `out of order results drain only after missing earlier segments arrive`() {
        val queue = LongFormSegmentQueue()
        val first = queue.reserve()
        val second = queue.reserve()
        val third = queue.reserve()

        assertEquals(emptyList(), queue.complete(third, " third ").readyText)
        assertEquals(listOf("first"), queue.complete(first, " first ").readyText)
        val finalDrain = queue.complete(second, "second")

        assertEquals(listOf("second", "third"), finalDrain.readyText)
        assertEquals(0, finalDrain.inFlightCount)
        assertFalse(finalDrain.shouldFinish)
    }

    @Test fun `silent segments advance order and final segment finishes after a complete drain`() {
        val queue = LongFormSegmentQueue()
        val first = queue.reserve()
        val final = queue.reserveFinal()

        assertEquals(emptyList(), queue.complete(final, "").readyText)
        val drain = queue.complete(first, "spoken")

        assertEquals(listOf("spoken", ""), drain.readyText)
        assertEquals(0, drain.inFlightCount)
        assertTrue(drain.shouldFinish)
    }

    @Test fun `reset starts a new independent index sequence`() {
        val queue = LongFormSegmentQueue()
        assertEquals(0, queue.reserve())
        assertEquals(1, queue.reserveRescueTail())
        queue.reset()

        assertEquals(0, queue.reserve())
        assertEquals(1, queue.inFlightCount)
    }
}
