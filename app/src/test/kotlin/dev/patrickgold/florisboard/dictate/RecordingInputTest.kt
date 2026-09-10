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

import android.media.AudioDeviceInfo
import dev.patrickgold.florisboard.dictate.audio.RecordingInput
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingInputTest {
    @Test fun `capture routes distinguish built in SCO BLE wired and USB inputs`() {
        assertEquals(RecordingInput.PHONE, RecordingInput.fromDeviceType(AudioDeviceInfo.TYPE_BUILTIN_MIC))
        assertEquals(RecordingInput.BLUETOOTH, RecordingInput.fromDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertEquals(RecordingInput.BLUETOOTH, RecordingInput.fromDeviceType(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertEquals(RecordingInput.WIRED, RecordingInput.fromDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertEquals(RecordingInput.USB, RecordingInput.fromDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET))
    }

    @Test fun `unknown routes and output devices must never be labelled phone or Bluetooth mic`() {
        assertEquals(RecordingInput.UNKNOWN, RecordingInput.fromDeviceType(AudioDeviceInfo.TYPE_UNKNOWN))
        assertEquals(RecordingInput.OTHER, RecordingInput.fromDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(RecordingInput.OTHER, RecordingInput.fromDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    }
}
