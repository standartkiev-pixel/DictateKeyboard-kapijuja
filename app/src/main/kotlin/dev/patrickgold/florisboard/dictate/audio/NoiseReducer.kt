/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Experimental, adjustable noise suppression for dictation (Kapijuja).
 *
 * The phone's own voice processing (and the old fixed behaviour) can be strong enough to swallow quiet
 * words. This reducer is gentle by construction: a short-time spectral gain (Wiener-style subtraction)
 * with a hard *floor*, so no frequency is ever attenuated by more than [maxAttenuationDb]. At the default
 * level 1 that is only 2.5 dB, so speech is never removed — it only takes the edge off steady background
 * hiss/hum. Level 10 allows 25 dB with doubled over-subtraction (the strong setting).
 *
 * Streaming, 16 kHz mono PCM16: 512-sample frames, 50 % overlap, sqrt-Hann analysis/synthesis (which
 * reconstructs the input exactly when the gain is 1). The noise floor is tracked per bin with a
 * minimum follower that falls fast and rises slowly; erring low only means *less* suppression.
 * Gains open instantly and close slowly so word endings are not chopped.
 *
 * Total output length always equals total input length once [flush] is called; between calls the
 * output lags by [HOP] samples (16 ms).
 */
class NoiseReducer(level: Int) {

    val level: Int = level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    /** Largest attenuation any bin may receive, in dB. */
    val maxAttenuationDb: Float = this.level * DB_PER_LEVEL
    private val floorGain: Float = 10f.pow(-maxAttenuationDb / 20f)
    private val overSubtraction: Float = 1f + this.level / MAX_LEVEL.toFloat()

    private val window = FloatArray(N) { sqrt(0.5f - 0.5f * cos(2.0 * PI * it / N).toFloat()) }
    private val prevHop = FloatArray(HOP)
    private val hopBuf = FloatArray(HOP)
    private var hopFill = 0
    private val outAccum = FloatArray(N)
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val bins = N / 2 + 1
    private val smoothPower = FloatArray(bins)
    private val noise = FloatArray(bins)
    private val gain = FloatArray(bins) { 1f }
    private val rawGain = FloatArray(bins)
    private var frames = 0

    private var totalIn = 0L
    private var totalOut = 0L
    private var toSkip = HOP

    private var outBytes = ByteArray(0)
    private var outLen = 0

    /** Processes [len] bytes of little-endian PCM16; returns a buffer and the number of valid bytes in it. */
    fun process(pcm: ByteArray, len: Int): Pair<ByteArray, Int> {
        outLen = 0
        ensureOut(len + HOP * 2)
        var i = 0
        while (i + 1 < len) {
            val s = ((pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
            push(s / 32768f)
            totalIn++
            i += 2
        }
        return outBytes to outLen
    }

    /** Emits the delayed tail so the output is exactly as long as everything that was fed in. */
    fun flush(): Pair<ByteArray, Int> {
        outLen = 0
        ensureOut(N * 4)
        var guard = 0
        while (totalOut < totalIn && guard++ < 4 * N) push(0f)
        return outBytes to outLen
    }

    private fun push(sample: Float) {
        hopBuf[hopFill++] = sample
        if (hopFill == HOP) {
            processFrame()
            hopFill = 0
        }
    }

    private fun processFrame() {
        for (n in 0 until HOP) {
            re[n] = prevHop[n] * window[n]
            re[n + HOP] = hopBuf[n] * window[n + HOP]
        }
        im.fill(0f)
        fft(re, im, inverse = false)

        frames++
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            val sp = if (frames == 1) p else POWER_SMOOTH * smoothPower[k] + (1f - POWER_SMOOTH) * p
            smoothPower[k] = sp
            val nk = noise[k]
            noise[k] = when {
                frames == 1 -> sp
                sp < nk -> NOISE_FALL * nk + (1f - NOISE_FALL) * sp
                else -> nk * NOISE_RISE
            }
            val g = if (sp <= 1e-12f) floorGain else 1f - overSubtraction * noise[k] / sp
            rawGain[k] = g.coerceIn(floorGain, 1f)
        }
        for (k in 0 until bins) {
            // Light smoothing across neighbouring bins tames "musical noise".
            val a = rawGain[max(0, k - 1)]
            val b = rawGain[k]
            val c = rawGain[min(bins - 1, k + 1)]
            val g = 0.25f * a + 0.5f * b + 0.25f * c
            // Open instantly, close slowly: word endings survive.
            gain[k] = if (g >= gain[k]) g else GAIN_RELEASE * gain[k] + (1f - GAIN_RELEASE) * g
        }
        for (k in 0 until bins) {
            val g = gain[k]
            re[k] *= g; im[k] *= g
            if (k != 0 && k != N / 2) {
                re[N - k] *= g; im[N - k] *= g
            }
        }
        fft(re, im, inverse = true)
        for (n in 0 until N) outAccum[n] += re[n] * window[n]
        for (n in 0 until HOP) emit(outAccum[n])
        System.arraycopy(outAccum, HOP, outAccum, 0, N - HOP)
        outAccum.fill(0f, N - HOP, N)
        System.arraycopy(hopBuf, 0, prevHop, 0, HOP)
    }

    private fun emit(v: Float) {
        if (toSkip > 0) { toSkip--; return }
        if (totalOut >= totalIn) return
        val s = (v * 32768f).roundToInt().coerceIn(-32768, 32767)
        ensureOut(outLen + 2)
        outBytes[outLen++] = (s and 0xff).toByte()
        outBytes[outLen++] = ((s shr 8) and 0xff).toByte()
        totalOut++
    }

    private fun ensureOut(size: Int) {
        if (outBytes.size < size) outBytes = outBytes.copyOf(max(size, outBytes.size * 2))
    }

    private val bitRev = IntArray(N).also { rev ->
        var bitsN = 0
        while ((1 shl bitsN) < N) bitsN++
        for (i in 0 until N) {
            var x = i; var r = 0
            repeat(bitsN) { r = (r shl 1) or (x and 1); x = x shr 1 }
            rev[i] = r
        }
    }
    private val cosT = FloatArray(N / 2) { cos(2.0 * PI * it / N).toFloat() }
    private val sinT = FloatArray(N / 2) { sin(2.0 * PI * it / N).toFloat() }

    private fun fft(xr: FloatArray, xi: FloatArray, inverse: Boolean) {
        for (i in 0 until N) {
            val j = bitRev[i]
            if (j > i) {
                var t = xr[i]; xr[i] = xr[j]; xr[j] = t
                t = xi[i]; xi[i] = xi[j]; xi[j] = t
            }
        }
        var size = 2
        while (size <= N) {
            val half = size / 2
            val step = N / size
            var start = 0
            while (start < N) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]
                    val wi = if (inverse) sinT[k * step] else -sinT[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = wr * xr[b] - wi * xi[b]
                    val ti = wr * xi[b] + wi * xr[b]
                    xr[b] = xr[a] - tr; xi[b] = xi[a] - ti
                    xr[a] += tr; xi[a] += ti
                }
                start += size
            }
            size *= 2
        }
        if (inverse) {
            val inv = 1f / N
            for (i in 0 until N) { xr[i] *= inv; xi[i] *= inv }
        }
    }

    companion object {
        const val MIN_LEVEL = 0
        const val MAX_LEVEL = 10
        /** Default: the weakest audible setting, which cannot remove speech. */
        const val DEFAULT_LEVEL = 1
        /** From this level on, the phone's own noise suppressor (if the device exposes one) stays enabled. */
        const val PLATFORM_SUPPRESSOR_LEVEL = 8
        const val DB_PER_LEVEL = 2.5f

        const val N = 512
        const val HOP = N / 2
        private const val POWER_SMOOTH = 0.6f
        private const val NOISE_FALL = 0.8f
        /** ≈ +2.7 dB per second at 62.5 frames/s: slow enough that sustained speech is not learned as noise. */
        private const val NOISE_RISE = 1.01f
        private const val GAIN_RELEASE = 0.85f

        /**
         * Silero VAD speech threshold for the silence gate / pause trimmer. The old fixed 0.5 equals level
         * 10; weaker levels accept quieter speech as speech, so the trimmer stops cutting soft words.
         */
        fun vadThreshold(level: Int): Float = 0.30f + 0.02f * level.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }
}
