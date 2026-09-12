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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionWatchdogTest {
    @Test fun `one absolute deadline expires once without a real-time wait`() = runTest {
        val expirations = mutableListOf<Long>()
        val watchdog = TranscriptionWatchdog(this, { testScheduler.currentTime })

        watchdog.start(120_000L, isRequestActive = { true }) { elapsedMs, _ ->
            expirations += elapsedMs
        }
        runCurrent()
        assertEquals(120L, watchdog.countdown.value?.secondsRemaining)

        advanceTimeBy(119_999L)
        runCurrent()
        assertEquals(1L, watchdog.countdown.value?.secondsRemaining)
        assertEquals(emptyList(), expirations)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(120_000L), expirations)
        assertNull(watchdog.countdown.value)
    }

    @Test fun `old generation cannot clear a replacement countdown`() = runTest {
        val watchdog = TranscriptionWatchdog(this, { testScheduler.currentTime })

        watchdog.start(120_000L, isRequestActive = { true }) { _, _ -> }
        runCurrent()
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(100L, watchdog.countdown.value?.secondsRemaining)

        watchdog.start(120_000L, isRequestActive = { true }) { _, _ -> }
        runCurrent()
        assertEquals(120L, watchdog.countdown.value?.secondsRemaining)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(119L, watchdog.countdown.value?.secondsRemaining)
    }

    @Test fun `stop clears countdown and prevents later expiry`() = runTest {
        var expirations = 0
        val watchdog = TranscriptionWatchdog(this, { testScheduler.currentTime })

        watchdog.start(30_000L, isRequestActive = { true }) { _, _ -> expirations++ }
        runCurrent()
        watchdog.stop()
        runCurrent()
        assertNull(watchdog.countdown.value)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(0, expirations)
    }
}
