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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Routes audio capture through a connected Bluetooth (SCO) microphone, mirroring the legacy Dictate
 * behaviour but split out into a small, self-contained helper.
 *
 * Two code paths depending on the platform:
 *  - API 31+ uses the modern, synchronous [AudioManager.setCommunicationDevice].
 *  - API 26–30 uses the deprecated `startBluetoothSco()` and waits for the SCO-connected broadcast
 *    (with a short timeout) before the recorder is started, so we don't capture silence.
 *
 * [activate] returns true when Android accepts the Bluetooth communication-route request; the
 * caller should use VOICE_COMMUNICATION and inspect AudioRecord.routedDevice after capture starts.
 * This request alone does not prove which microphone supplies the samples. On false (no device,
 * timeout, error) the caller falls back to the local mic. [deactivate] must always be called once
 * recording ends, regardless of the [activate] result.
 */
internal interface BluetoothInputRoute {
    val isActivated: Boolean
    fun bluetoothInputDevice(): AudioDeviceInfo?
    fun phoneInputDevice(): AudioDeviceInfo?
    suspend fun activate(): Boolean
    fun deactivate()
}

internal class BluetoothMicRouter(context: Context) : BluetoothInputRoute {

    private val appContext = context.applicationContext
    private val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var activated = false
    override val isActivated: Boolean get() = activated

    /** Bluetooth SCO input currently exposed by Android, if one is connected. */
    override fun bluetoothInputDevice(): AudioDeviceInfo? = inputDevice(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)

    /** Built-in handset microphone used when Bluetooth routing is turned off. */
    override fun phoneInputDevice(): AudioDeviceInfo? = inputDevice(AudioDeviceInfo.TYPE_BUILTIN_MIC)

    /** Whether a Bluetooth SCO input device is currently present and usable. */
    fun isAvailable(): Boolean {
        if (!am.isBluetoothScoAvailableOffCall) return false
        return runCatching {
            am.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        }.getOrDefault(false)
    }

    /** Attempts to route capture to the Bluetooth mic. Returns true if it is now active. */
    override suspend fun activate(): Boolean {
        if (activated) return true
        if (!isAvailable()) return false
        activated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activateModern()
        } else {
            activateLegacy()
        }
        return activated
    }

    /** Stops Bluetooth routing if it was activated. Safe to call unconditionally. */
    override fun deactivate() {
        if (!activated) return
        activated = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.stopBluetoothSco()
            }
        }
    }

    private fun activateModern(): Boolean {
        val device = am.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        } ?: return false
        return runCatching { am.setCommunicationDevice(device) }.getOrDefault(false)
    }

    private fun inputDevice(type: Int): AudioDeviceInfo? = runCatching {
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == type }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private suspend fun activateLegacy(): Boolean {
        if (am.isBluetoothScoOn) return true
        return try {
            withTimeout(SCO_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val state = intent.getIntExtra(
                                AudioManager.EXTRA_SCO_AUDIO_STATE,
                                AudioManager.SCO_AUDIO_STATE_ERROR,
                            )
                            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED && cont.isActive) {
                                runCatching { appContext.unregisterReceiver(this) }
                                cont.resume(true)
                            }
                        }
                    }
                    val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        appContext.registerReceiver(receiver, filter)
                    }
                    cont.invokeOnCancellation {
                        runCatching { appContext.unregisterReceiver(receiver) }
                    }
                    am.startBluetoothSco()
                }
            }
        } catch (_: TimeoutCancellationException) {
            runCatching { am.stopBluetoothSco() }
            false
        }
    }

    companion object {
        private const val SCO_TIMEOUT_MS = 2500L
    }
}
