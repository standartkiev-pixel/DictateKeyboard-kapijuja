/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import dev.patrickgold.florisboard.dictate.audio.NoiseReducer
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoiseReducerTest {

    private fun toPcm(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, f ->
            val s = (f * 32767f).toInt().coerceIn(-32768, 32767)
            out[2 * i] = (s and 0xff).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xff).toByte()
        }
        return out
    }

    private fun fromPcm(bytes: ByteArray): FloatArray = FloatArray(bytes.size / 2) { i ->
        ((bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f
    }

    /** Feeds the input in uneven chunks (like AudioRecord does) and flushes. */
    private fun run(level: Int, input: FloatArray): FloatArray {
        val nr = NoiseReducer(level)
        val pcm = toPcm(input)
        val out = ByteArrayOutputStream()
        var i = 0
        var chunk = 700
        while (i < pcm.size) {
            val len = minOf(chunk, pcm.size - i)
            val slice = pcm.copyOfRange(i, i + len)
            val (b, n) = nr.process(slice, len)
            out.write(b, 0, n)
            i += len
            chunk = if (chunk == 700) 1280 else 700
        }
        val (b, n) = nr.flush()
        out.write(b, 0, n)
        return fromPcm(out.toByteArray())
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return sqrt(sum / (to - from))
    }

    @Test
    fun `output length equals input length`() {
        val input = FloatArray(16_000 + 123) { Random(1).nextFloat() * 0.01f }
        assertEquals(input.size, run(5, input).size)
    }

    @Test
    fun `loud tone survives the default level almost untouched`() {
        val input = FloatArray(32_000) { 0.5f * sin(2 * PI * 440.0 * it / 16_000).toFloat() }
        val out = run(NoiseReducer.DEFAULT_LEVEL, input)
        val ratio = rms(out, 8_000, 30_000) / rms(input, 8_000, 30_000)
        assertTrue(ratio > 0.7, "tone ratio $ratio")
    }

    @Test
    fun `default level never attenuates more than its floor`() {
        val rnd = Random(7)
        val input = FloatArray(48_000) { (rnd.nextFloat() - 0.5f) * 0.05f }
        val out = run(NoiseReducer.DEFAULT_LEVEL, input)
        val ratio = rms(out, 24_000, 46_000) / rms(input, 24_000, 46_000)
        // 2.5 dB floor ≈ 0.75 amplitude; allow window/rounding slack.
        assertTrue(ratio > 0.65, "noise ratio at level 1 $ratio")
    }

    @Test
    fun `strong level clearly reduces steady noise`() {
        val rnd = Random(7)
        val input = FloatArray(48_000) { (rnd.nextFloat() - 0.5f) * 0.05f }
        val weak = rms(run(1, input), 24_000, 46_000)
        val strong = rms(run(10, input), 24_000, 46_000)
        assertTrue(strong < weak * 0.6, "strong=$strong weak=$weak")
    }

    @Test
    fun `vad threshold keeps old value at max level`() {
        assertEquals(0.5f, NoiseReducer.vadThreshold(10), 1e-6f)
        assertTrue(NoiseReducer.vadThreshold(1) < 0.4f)
    }
}
