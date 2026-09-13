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

class LongFormSessionTest {
    @Test fun `begin creates a clean independent lifecycle`() {
        val session = LongFormSession()
        session.begin(keepAudio = true)
        session.rememberRecordedSeconds(601L)
        assertEquals(0, session.reserveSegment())
        assertTrue(session.isActive)
        assertTrue(session.keepAudio)
        assertEquals(601L, session.recordedSeconds)

        session.resetRuntimeState()
        session.begin(keepAudio = false)

        assertTrue(session.isActive)
        assertFalse(session.keepAudio)
        assertEquals(0L, session.recordedSeconds)
        assertEquals(0, session.reserveSegment())
    }

    @Test fun `cancellation guard survives runtime reset until rescue completes`() {
        val session = LongFormSession()
        session.begin(keepAudio = false)

        assertTrue(session.beginCancellation())
        assertFalse(session.beginCancellation())
        session.resetRuntimeState()
        assertFalse(session.isActive)
        assertTrue(session.cancellationPending)

        session.finishCancellation()
        assertFalse(session.cancellationPending)
        assertTrue(session.beginCancellation())
    }

    @Test fun `final reservation finishes only after every result lands`() {
        val session = LongFormSession()
        session.begin(keepAudio = false)
        val first = session.reserveSegment()
        val final = session.reserveFinalSegment()

        assertFalse(session.completeSegment(final, "second").shouldFinish)
        val drain = session.completeSegment(first, "first")

        assertEquals(listOf("first", "second"), drain.readyText)
        assertEquals(0, session.inFlightCount)
        assertTrue(drain.shouldFinish)
    }
}
