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
import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptionCountdownTest {
    @Test fun `expiry matches watchdog and never displays zero early`() {
        assertEquals(1L, TranscriptionCountdown.afterIdle(120_000L, 119_999L).secondsRemaining)
        assertEquals(0L, TranscriptionCountdown.afterIdle(120_000L, 120_000L).secondsRemaining)
        assertEquals(0f, TranscriptionCountdown.afterIdle(120_000L, 130_000L).fractionRemaining)
    }

    @Test fun `heartbeat restores the actual no-progress budget`() {
        assertEquals(0.5f, TranscriptionCountdown.afterIdle(120_000L, 60_000L).fractionRemaining)
        assertEquals(120L, TranscriptionCountdown.afterIdle(120_000L, 0L).secondsRemaining)
        assertEquals(1f, TranscriptionCountdown.afterIdle(120_000L, -1L).fractionRemaining)
    }

    @Test fun `late cancelled worker cannot renew the watchdog`() {
        val oldJob = Job()
        var heartbeats = 0
        val oldProgress = activeTranscriptionProgress(oldJob) { heartbeats++ }
        oldProgress()
        oldJob.cancel()
        oldProgress()
        val newJob = Job()
        activeTranscriptionProgress(newJob) { heartbeats++ }()
        oldProgress()
        assertEquals(2, heartbeats)
        newJob.cancel()
    }
}
