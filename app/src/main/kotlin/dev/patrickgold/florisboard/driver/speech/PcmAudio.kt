/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.driver.speech

/** Small helpers for the 16-bit mono PCM every cloud engine is asked to return. */
object PcmAudio {
    /** All cloud engines are requested at this rate, so one player can play any of them. */
    const val SAMPLE_RATE = 24_000
    const val BYTES_PER_FRAME = 2

    fun bytesForMillis(ms: Int, sampleRate: Int = SAMPLE_RATE): Int =
        (sampleRate.toLong() * BYTES_PER_FRAME * ms / 1000L).toInt().let { it - it % BYTES_PER_FRAME }

    /**
     * Number of leading bytes that form a RIFF/WAVE header, or 0 when [head] does not start with
     * one. Some providers wrap PCM in WAV even when asked for raw PCM; the header would play as a
     * click. Returns -1 when [head] starts like a WAV file but the `data` chunk is not inside it yet.
     */
    fun wavHeaderLength(head: ByteArray, length: Int = head.size): Int {
        if (length < 12) return if (startsWith(head, length, RIFF)) -1 else 0
        if (!startsWith(head, length, RIFF) || !matchesAt(head, 8, WAVE)) return 0
        var offset = 12
        while (offset + 8 <= length) {
            val chunkSize = readIntLe(head, offset + 4)
            if (matchesAt(head, offset, DATA)) return offset + 8
            if (chunkSize < 0) return 0
            offset += 8 + chunkSize + (chunkSize and 1)
        }
        return -1
    }

    private fun startsWith(bytes: ByteArray, length: Int, prefix: ByteArray): Boolean {
        val n = minOf(length, prefix.size)
        for (i in 0 until n) if (bytes[i] != prefix[i]) return false
        return true
    }

    private fun matchesAt(bytes: ByteArray, offset: Int, tag: ByteArray): Boolean {
        if (offset + tag.size > bytes.size) return false
        for (i in tag.indices) if (bytes[offset + i] != tag[i]) return false
        return true
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WAVE = "WAVE".toByteArray(Charsets.US_ASCII)
    private val DATA = "data".toByteArray(Charsets.US_ASCII)
}

/**
 * Turns an arbitrary sequence of byte reads into whole 16-bit frames, dropping a leading WAV header
 * if the provider sent one. A read can end in the middle of a sample; the odd byte is carried to the
 * next read instead of being played as noise.
 */
class PcmFrameAligner {
    private var header: ByteArray? = ByteArray(0)
    private var carry: Int = -1

    /** Returns the whole frames ready to play from this read, or null if there are none yet. */
    fun accept(bytes: ByteArray, length: Int = bytes.size): ByteArray? {
        if (length <= 0) return null
        var data = bytes.copyOf(length)
        val pendingHeader = header
        if (pendingHeader != null) {
            val joined = pendingHeader + data
            val headerLength = PcmAudio.wavHeaderLength(joined)
            when {
                headerLength < 0 && joined.size < MAX_HEADER -> {
                    header = joined
                    return null
                }
                headerLength < 0 -> {
                    header = null
                    data = joined
                }
                else -> {
                    header = null
                    data = joined.copyOfRange(headerLength, joined.size)
                }
            }
        }
        if (carry >= 0) {
            data = byteArrayOf(carry.toByte()) + data
            carry = -1
        }
        if (data.size % 2 == 1) {
            carry = data.last().toInt() and 0xFF
            data = data.copyOf(data.size - 1)
        }
        return data.takeIf { it.isNotEmpty() }
    }

    /** Releases bytes still held back because they looked like the start of a header. */
    fun finish(): ByteArray? {
        val pendingHeader = header ?: return null
        header = null
        if (pendingHeader.isEmpty() || PcmAudio.wavHeaderLength(pendingHeader) != 0) return null
        return accept(pendingHeader)
    }

    private companion object {
        const val MAX_HEADER = 512
    }
}
