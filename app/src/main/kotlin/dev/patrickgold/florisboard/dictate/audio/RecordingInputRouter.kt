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

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies the Bluetooth microphone preference before and during one recording session.
 *
 * Mid-recording changes keep the same [RecordingController] and WAV file. Android's preferred-input
 * routing moves the active AudioRecord between the Bluetooth SCO device and the built-in microphone,
 * so already captured speech is never discarded. [isCurrent] makes rapid taps converge on the newest
 * preference even when establishing a legacy SCO connection takes a few seconds.
 */
internal class RecordingInputRouter internal constructor(
    private val bluetooth: BluetoothInputRoute,
) {
    constructor(context: Context) : this(BluetoothMicRouter(context))

    private val routeMutex = Mutex()
    private var preferInputDevice: ((AudioDeviceInfo?) -> Boolean)? = null

    /** Selects the AudioRecord source used when a recording session is first created. */
    suspend fun sourceForStart(bluetoothEnabled: Boolean, localSource: Int): Int =
        if (bluetoothEnabled && bluetooth.activate()) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            localSource
        }

    /** Applies the already prepared Bluetooth device after AudioRecord has been created. */
    fun bind(recorder: RecordingController) {
        bindInputPreference(recorder::preferInputDevice)
    }

    /** Injectable device sink keeps rapid-route behavior deterministic in local unit tests. */
    internal fun bindInputPreference(prefer: (AudioDeviceInfo?) -> Boolean) {
        preferInputDevice = prefer
        if (bluetooth.isActivated) {
            prefer(bluetooth.bluetoothInputDevice())
        }
    }

    /** Changes the active input; if Bluetooth is unavailable, recording continues on its current input. */
    fun applyBluetoothPreference(
        scope: CoroutineScope,
        enabled: Boolean,
        isCurrent: () -> Boolean,
    ) {
        scope.launch {
            routeMutex.withLock {
                val prefer = preferInputDevice ?: return@withLock
                if (!isCurrent()) return@withLock
                if (!enabled) {
                    bluetooth.deactivate()
                    prefer(bluetooth.phoneInputDevice())
                    return@withLock
                }

                if (!bluetooth.activate()) return@withLock
                if (!isCurrent()) {
                    bluetooth.deactivate()
                    return@withLock
                }
                // setCommunicationDevice/SCO is authoritative. setPreferredDevice reinforces that
                // request for this AudioRecord but may legally return false on vendor implementations.
                prefer(bluetooth.bluetoothInputDevice())
            }
        }
    }

    /** Releases any communication-device routing owned by this recording session. */
    fun close() {
        preferInputDevice = null
        bluetooth.deactivate()
    }
}
