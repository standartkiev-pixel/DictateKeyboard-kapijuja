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

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Gemini speech generation (`generateContent` with an AUDIO response) with the user's key.
 * Gemini returns the whole piece as base64 24 kHz 16-bit PCM in one JSON answer, so this source is
 * the one that benefits most from the short first piece and the one-ahead prefetch in the player.
 */
object GeminiSpeechSource : CloudSpeechSource {
    override val id = SpeechEngineId.GOOGLE
    const val DEFAULT_MODEL = "gemini-2.5-flash-preview-tts"

    override fun isConfigured(settings: SpeechSettings): Boolean = settings.googleKey.isNotBlank()

    override suspend fun synthesize(text: String, settings: SpeechSettings, onPcm: suspend (ByteArray) -> Unit) {
        val model = settings.googleModel.filter { it.isLetterOrDigit() || it in "-._" }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", settings.googleKey)
            .post(requestJson(text, settings).toRequestBody("application/json".toMediaType()))
            .build()
        val pcm = SpeechHttp.execute(request) { response ->
            SpeechHttp.requireSuccess("Gemini", response)
            val body = response.body?.string().orEmpty()
            extractPcm(body) ?: throw SpeechEngineException("Gemini answered without audio", billingMayApply = true)
        }
        val aligner = PcmFrameAligner()
        // Hand the audio over in ~200 ms blocks so a stop is honoured quickly.
        val block = PcmAudio.bytesForMillis(200)
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(pcm.size, offset + block)
            aligner.accept(pcm.copyOfRange(offset, end))?.let { onPcm(it) }
            offset = end
        }
        aligner.finish()?.let { onPcm(it) }
    }

    internal fun requestJson(text: String, settings: SpeechSettings): String = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                putJsonArray("parts") {
                    addJsonObject { put("text", text) }
                }
            }
        }
        putJsonObject("generationConfig") {
            putJsonArray("responseModalities") { add("AUDIO") }
            putJsonObject("speechConfig") {
                putJsonObject("voiceConfig") {
                    putJsonObject("prebuiltVoiceConfig") { put("voiceName", settings.googleVoice) }
                }
            }
        }
    }.toString()

    /** Concatenated PCM of every `inlineData` part in the first candidate, or null if there is none. */
    internal fun extractPcm(body: String): ByteArray? {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return null
        val chunks = parts.mapNotNull { part ->
            val inline = (part as? JsonObject)?.get("inlineData")?.jsonObject ?: return@mapNotNull null
            inline["data"]?.jsonPrimitive?.content?.let { Base64.getMimeDecoder().decode(it) }
        }
        if (chunks.isEmpty()) return null
        return chunks.reduce { acc, bytes -> acc + bytes }
    }
}
