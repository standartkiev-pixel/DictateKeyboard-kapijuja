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

import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingInputRouterTest {
    @Test fun `Bluetooth at recording start selects communication audio source`() = runTest {
        val route = FakeBluetoothRoute()
        val router = RecordingInputRouter(route)

        assertEquals(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            router.sourceForStart(bluetoothEnabled = true, localSource = MediaRecorder.AudioSource.MIC),
        )
        assertEquals(1, route.activateCalls)
    }

    @Test fun `latest preference wins while Bluetooth activation is pending`() = runTest {
        val activationGate = CompletableDeferred<Unit>()
        val route = FakeBluetoothRoute(activationGate)
        val router = RecordingInputRouter(route)
        var preferredDeviceCalls = 0
        val persisted = mutableListOf<Boolean>()
        router.sourceForStart(bluetoothEnabled = false, localSource = MediaRecorder.AudioSource.MIC)
        router.bindInputPreference {
            preferredDeviceCalls++
            true
        }

        router.toggleBluetoothPreference(this, persist = { persisted += it }, isRecording = { true })
        runCurrent()
        router.toggleBluetoothPreference(this, persist = { persisted += it }, isRecording = { true })
        activationGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(true, false), persisted)
        assertEquals(0, route.bluetoothDeviceCalls)
        assertEquals(1, route.phoneDeviceCalls)
        assertEquals(1, preferredDeviceCalls)
        assertEquals(2, route.deactivateCalls)
    }

    private class FakeBluetoothRoute(
        private val activationGate: CompletableDeferred<Unit>? = null,
    ) : BluetoothInputRoute {
        override var isActivated = false
        var activateCalls = 0
        var deactivateCalls = 0
        var bluetoothDeviceCalls = 0
        var phoneDeviceCalls = 0

        override fun bluetoothInputDevice(): AudioDeviceInfo? {
            bluetoothDeviceCalls++
            return null
        }

        override fun phoneInputDevice(): AudioDeviceInfo? {
            phoneDeviceCalls++
            return null
        }

        override suspend fun activate(): Boolean {
            activateCalls++
            activationGate?.await()
            isActivated = true
            return true
        }

        override fun deactivate() {
            deactivateCalls++
            isActivated = false
        }
    }
}
