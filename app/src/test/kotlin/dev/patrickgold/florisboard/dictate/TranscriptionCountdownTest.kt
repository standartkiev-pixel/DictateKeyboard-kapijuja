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

class TranscriptionCountdownTest {
    @Test fun `expiry matches watchdog and never displays zero early`() {
        assertEquals(1L, TranscriptionCountdown.afterElapsed(120_000L, 119_999L).secondsRemaining)
        assertEquals(0L, TranscriptionCountdown.afterElapsed(120_000L, 120_000L).secondsRemaining)
        assertEquals(0f, TranscriptionCountdown.afterElapsed(120_000L, 130_000L).fractionRemaining)
    }

    @Test fun `elapsed time can never restore the request budget`() {
        assertEquals(120L, TranscriptionCountdown.afterElapsed(120_000L, 0L).secondsRemaining)
        assertEquals(100L, TranscriptionCountdown.afterElapsed(120_000L, 20_000L).secondsRemaining)
        assertEquals(30L, TranscriptionCountdown.afterElapsed(120_000L, 90_000L).secondsRemaining)
        assertEquals(1f, TranscriptionCountdown.afterElapsed(120_000L, -1L).fractionRemaining)
    }
}
