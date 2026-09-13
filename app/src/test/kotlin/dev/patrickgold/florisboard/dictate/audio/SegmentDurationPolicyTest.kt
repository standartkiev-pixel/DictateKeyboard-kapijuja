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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentDurationPolicyTest {
    @Test fun `continuous ten minute capture cuts at each three minute boundary`() {
        val sampleRate = 16_000
        val policy = SegmentDurationPolicy(maxSamples = 3L * 60L * sampleRate)
        var cuts = 0

        repeat(10 * 60) {
            if (policy.onSamples(sampleRate)) cuts++
        }

        assertEquals(3, cuts)
        repeat(2 * 60 - 1) { assertFalse(policy.onSamples(sampleRate)) }
        assertTrue(policy.onSamples(sampleRate))
    }

    @Test fun `manual or pause cut restarts the hard duration window`() {
        val policy = SegmentDurationPolicy(maxSamples = 100L)
        assertFalse(policy.onSamples(90))

        policy.reset()

        assertFalse(policy.onSamples(90))
        assertTrue(policy.onSamples(10))
    }

    @Test fun `frame remainder belongs to the next segment`() {
        val policy = SegmentDurationPolicy(maxSamples = 100L)

        assertTrue(policy.onSamples(120))
        assertFalse(policy.onSamples(79))
        assertTrue(policy.onSamples(1))
    }
}
