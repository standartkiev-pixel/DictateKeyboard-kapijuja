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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * The models an OpenAI key can use for reading aloud, asked from the account itself so the list in
 * settings stays right when OpenAI adds or retires one. Speech goes through the Realtime API (see
 * [OpenAiRealtimeSpeechSource]), so these are the account's realtime models.
 */
internal object OpenAiSpeechModels {
    suspend fun fetch(apiKey: String): List<String> {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .build()
        return SpeechHttp.execute(request) { response ->
            SpeechHttp.requireSuccess("OpenAI", response)
            parse(response.body?.string().orEmpty())
        }
    }

    /** Ids of the speech-capable models in a `/v1/models` answer, the default one first. */
    fun parse(json: String): List<String> {
        val data = runCatching { Json.parseToJsonElement(json).jsonObject["data"] as? JsonArray }.getOrNull()
        return data.orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
            .filter(::isSpeechModel)
            .distinct()
            .sortedWith(compareBy({ it != OpenAiRealtimeSpeechSource.DEFAULT_MODEL }, { it }))
    }

    fun isSpeechModel(id: String): Boolean =
        "realtime" in id && "transcribe" !in id && "whisper" !in id
}
