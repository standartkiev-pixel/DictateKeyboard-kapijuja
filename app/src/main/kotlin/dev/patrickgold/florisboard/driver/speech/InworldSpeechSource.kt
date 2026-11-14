/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.speech

import java.util.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Inworld's official NDJSON streaming TTS endpoint. Each result.audioContent contains Base64
 * signed 16-bit little-endian PCM at the requested 24 kHz, not an MP3 or a WAV header.
 * A socket cancellation interrupts synthesis, and the caller prefetches at most one text chunk.
 */
object InworldSpeechSource : CloudSpeechSource {
    override val id: SpeechEngineId = SpeechEngineId.INWORLD

    override fun isConfigured(settings: SpeechSettings): Boolean =
        settings.inworldKey.isNotBlank() && settings.inworldVoice.isNotBlank()

    override suspend fun synthesize(
        text: String,
        settings: SpeechSettings,
        onPcm: suspend (ByteArray) -> Unit,
    ) {
        if (!isConfigured(settings)) throw SpeechEngineException("Inworld: API key or voice not selected")
        // This streaming endpoint accepts 4,000 UTF-16 units; SpeechTextChunker uses <=900.
        val request = Request.Builder()
            .url("https://api.inworld.ai/tts/v1/voice:stream")
            .header("Authorization", "Basic ${settings.inworldKey.removePrefix("Basic ").trim()}")
            .header("Accept", "application/json")
            .post(requestJson(text, settings).toRequestBody("application/json".toMediaType()))
            .build()
        SpeechHttp.execute(request) { response ->
            SpeechHttp.requireSuccess("Inworld", response)
            val aligner = PcmFrameAligner()
            var received = false
            val body = response.body ?: throw SpeechEngineException("Inworld returned empty body", billingMayApply = true)
            body.charStream().buffered().use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val root = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
                        ?: throw SpeechEngineException("Inworld returned invalid audio JSON", billingMayApply = true)
                    if (root["error"] != null) throw SpeechEngineException("Inworld stream error", billingMayApply = true)
                    val result = root["result"] as? JsonObject ?: root
                    val encoded = result["audioContent"]?.jsonPrimitive?.content.orEmpty()
                    if (encoded.isNotBlank()) {
                        val bytes = runCatching { Base64.getMimeDecoder().decode(encoded) }.getOrNull()
                            ?: throw SpeechEngineException("Inworld returned invalid audio encoding", billingMayApply = true)
                        aligner.accept(bytes)?.let { received = true; onPcm(it) }
                    }
                }
            }
            aligner.finish()?.let { received = true; onPcm(it) }
            if (!received) throw SpeechEngineException("Inworld returned no audio", billingMayApply = true)
        }
    }

    internal fun requestJson(text: String, settings: SpeechSettings): String = buildJsonObject {
        put("text", text)
        put("voiceId", settings.inworldVoice)
        put("modelId", settings.inworldModel)
        putJsonObject("audioConfig") {
            put("audioEncoding", "PCM")
            put("sampleRateHertz", PcmAudio.SAMPLE_RATE)
        }
    }.toString()
}
