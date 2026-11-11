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

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * xAI Grok text-to-speech (`POST /v1/tts`) with the user's key. Asks for raw PCM at 24 kHz and plays
 * the response body as it streams in. If the service wraps the PCM in a WAV header it is stripped.
 */
object XaiSpeechSource : CloudSpeechSource {
    override val id = SpeechEngineId.XAI
    private const val ENDPOINT = "https://api.x.ai/v1/tts"

    override fun isConfigured(settings: SpeechSettings): Boolean = settings.xaiKey.isNotBlank()

    override suspend fun synthesize(text: String, settings: SpeechSettings, onPcm: suspend (ByteArray) -> Unit) {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer ${settings.xaiKey}")
            .post(requestJson(text, settings).toRequestBody("application/json".toMediaType()))
            .build()
        SpeechHttp.execute(request) { response ->
            SpeechHttp.requireSuccess("xAI", response)
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.contains("mpeg") || contentType.contains("json")) {
                throw SpeechEngineException("xAI returned $contentType instead of PCM", billingMayApply = true)
            }
            val aligner = PcmFrameAligner()
            SpeechHttp.streamBody(response) { bytes, n -> aligner.accept(bytes, n)?.let { onPcm(it) } }
            aligner.finish()?.let { onPcm(it) }
        }
    }

    internal fun requestJson(text: String, settings: SpeechSettings): String = buildJsonObject {
        put("text", text)
        put("voice_id", settings.xaiVoice)
        put("language", settings.languageCode)
        put("speed", settings.speed.toDouble().coerceIn(0.7, 1.5))
        put("output_format", buildJsonObject {
            put("codec", "pcm")
            put("sample_rate", PcmAudio.SAMPLE_RATE)
        })
    }.toString()
}
