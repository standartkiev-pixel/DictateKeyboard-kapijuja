/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.speech

import java.net.URLEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ElevenLabs official Text-to-Speech API, written independently for this Apache-2.0 project.
 * Request raw signed 16-bit / 24 kHz mono PCM, so the existing continuous AudioTrack player
 * can play it without MP3 decoder gaps. One request per speech chunk, no automatic paid retry.
 */
object ElevenLabsSpeechSource : CloudSpeechSource {
    override val id: SpeechEngineId = SpeechEngineId.ELEVENLABS

    override fun isConfigured(settings: SpeechSettings): Boolean =
        settings.elevenKey.isNotBlank() && settings.elevenVoice.isNotBlank()

    override suspend fun synthesize(
        text: String,
        settings: SpeechSettings,
        onPcm: suspend (ByteArray) -> Unit,
    ) {
        if (!isConfigured(settings)) throw SpeechEngineException("ElevenLabs: API key or voice not selected")
        val voice = URLEncoder.encode(settings.elevenVoice, "UTF-8")
        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voice?output_format=pcm_24000")
            .header("xi-api-key", settings.elevenKey)
            .header("Accept", "audio/pcm")
            .post(requestJson(text, settings).toRequestBody("application/json".toMediaType()))
            .build()
        SpeechHttp.execute(request) { response ->
            SpeechHttp.requireSuccess("ElevenLabs", response)
            val mime = response.header("Content-Type").orEmpty().lowercase()
            if (mime.contains("json") || mime.contains("mpeg") || mime.contains("mp3")) {
                throw SpeechEngineException("ElevenLabs returned compressed or non-audio data", billingMayApply = true)
            }
            val aligner = PcmFrameAligner()
            var received = false
            SpeechHttp.streamBody(response) { bytes, length ->
                aligner.accept(bytes, length)?.let { received = true; onPcm(it) }
            }
            aligner.finish()?.let { received = true; onPcm(it) }
            if (!received) throw SpeechEngineException("ElevenLabs returned empty audio", billingMayApply = true)
        }
    }

    internal fun requestJson(text: String, settings: SpeechSettings): String = buildJsonObject {
        put("text", text)
        put("model_id", settings.elevenModel)
        // The cloud reader's speed setting is not forwarded to Eleven v3: voice_settings
        // support differs by model and sending unsupported parameters can cost an extra retry.
    }.toString()
}
