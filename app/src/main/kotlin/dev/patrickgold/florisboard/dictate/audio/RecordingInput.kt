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

/** Value-only route snapshot: no AudioRecord, Context or device handle escapes to the UI. */
enum class RecordingInput {
    UNKNOWN, PHONE, BLUETOOTH, WIRED, USB, OTHER;

    companion object {
        fun fromDeviceType(type: Int): RecordingInput = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> PHONE
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> WIRED
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> USB
            AudioDeviceInfo.TYPE_UNKNOWN -> UNKNOWN
            else -> OTHER
        }
    }
}
