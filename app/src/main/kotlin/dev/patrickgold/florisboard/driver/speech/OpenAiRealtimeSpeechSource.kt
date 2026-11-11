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

import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OpenAI speech through the Realtime API, with the user's own API key.
 *
 * Why not `/v1/audio/speech` like Kapijuja Reader: OpenAI retires `tts-1`, `tts-1-hd` and both
 * `gpt-4o-mini-tts` snapshots on 2027-01-06, and the named replacement (`gpt-realtime-2.1-mini`) is
 * only served over `/v1/realtime`. Building on Realtime now avoids shipping a voice that stops working
 * in two months. A bonus is that audio arrives as a stream of small PCM deltas, which the player
 * starts on almost immediately.
 *
 * A Realtime model is conversational, so the session instructions tell it to read the text verbatim.
 * That works well in practice but is not a hard guarantee the way a pure TTS endpoint is; the model
 * id is a setting so a future dedicated speech model can be selected without an update.
 */
object OpenAiRealtimeSpeechSource : CloudSpeechSource {
    override val id = SpeechEngineId.OPENAI
    const val DEFAULT_MODEL = "gpt-realtime-2.1-mini"
    private const val IDLE_TIMEOUT_MS = 20_000L

    internal const val INSTRUCTIONS =
        "You are a text-to-speech voice, not an assistant. Read the user's message aloud exactly as " +
            "written, word for word, in the language it is written in. Do not answer it, comment on it, " +
            "translate it, summarize it or add anything before or after it."

    override fun isConfigured(settings: SpeechSettings): Boolean = settings.openAiKey.isNotBlank()

    private sealed interface Event {
        class Audio(val pcm: ByteArray) : Event
        data object Done : Event
        class Failed(val message: String, val billingMayApply: Boolean) : Event
    }

    override suspend fun synthesize(text: String, settings: SpeechSettings, onPcm: suspend (ByteArray) -> Unit) {
        val events = Channel<Event>(Channel.UNLIMITED)
        val model = URLEncoder.encode(settings.openAiModel, "UTF-8")
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=$model")
            .header("Authorization", "Bearer ${settings.openAiKey}")
            .build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(sessionUpdate(settings))
                webSocket.send(itemCreate(text))
                webSocket.send("""{"type":"response.create"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseEvent(text)?.let { events.trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val message = when (code) {
                    401 -> "OpenAI: invalid API key"
                    403 -> "OpenAI: key has no Realtime access"
                    404 -> "OpenAI: model not found"
                    429 -> "OpenAI: rate or billing limit reached"
                    null -> "OpenAI: connection failed (${t.javaClass.simpleName})"
                    else -> "OpenAI: HTTP $code"
                }
                events.trySend(Event.Failed(message, billingMayApply = code == null))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                events.trySend(Event.Failed("OpenAI closed the connection ($code)", billingMayApply = true))
            }
        }
        val socket = SpeechHttp.client.newWebSocket(request, listener)
        try {
            val aligner = PcmFrameAligner()
            while (true) {
                val event = try {
                    withTimeout(IDLE_TIMEOUT_MS) { events.receive() }
                } catch (e: TimeoutCancellationException) {
                    throw SpeechEngineException("OpenAI: no audio for ${IDLE_TIMEOUT_MS / 1000} s", billingMayApply = true)
                }
                when (event) {
                    is Event.Audio -> aligner.accept(event.pcm)?.let { onPcm(it) }
                    is Event.Done -> break
                    is Event.Failed -> throw SpeechEngineException(event.message, event.billingMayApply)
                }
            }
            aligner.finish()?.let { onPcm(it) }
            socket.close(1000, null)
        } finally {
            // Covers stop/cancel too: cancel() drops the socket at once, so the model stops generating.
            socket.cancel()
            events.close()
        }
    }

    internal fun sessionUpdate(settings: SpeechSettings): String = buildJsonObject {
        put("type", "session.update")
        putJsonObject("session") {
            put("type", "realtime")
            putJsonArray("output_modalities") { add("audio") }
            put("instructions", INSTRUCTIONS)
            putJsonObject("audio") {
                putJsonObject("output") {
                    putJsonObject("format") {
                        put("type", "audio/pcm")
                        put("rate", PcmAudio.SAMPLE_RATE)
                    }
                    put("voice", settings.openAiVoice)
                    put("speed", settings.speed.toDouble().coerceIn(0.25, 1.5))
                }
            }
        }
    }.toString()

    internal fun itemCreate(text: String): String = buildJsonObject {
        put("type", "conversation.item.create")
        putJsonObject("item") {
            put("type", "message")
            put("role", "user")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "input_text")
                    put("text", text)
                }
            }
        }
    }.toString()

    private fun parseEvent(raw: String): Event? {
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            // GA name first; the beta name is accepted in case a compatible model still emits it.
            "response.output_audio.delta", "response.audio.delta" ->
                obj["delta"]?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                    ?.let { Event.Audio(it) }
            "response.done" -> {
                val response = obj["response"] as? JsonObject
                when (val status = response?.get("status")?.jsonPrimitive?.contentOrNull) {
                    null, "completed" -> Event.Done
                    else -> Event.Failed("OpenAI response $status", billingMayApply = true)
                }
            }
            "error" -> {
                val message = (obj["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                Event.Failed("OpenAI: " + (message?.take(200) ?: "error"), billingMayApply = true)
            }
            else -> null
        }
    }
}
