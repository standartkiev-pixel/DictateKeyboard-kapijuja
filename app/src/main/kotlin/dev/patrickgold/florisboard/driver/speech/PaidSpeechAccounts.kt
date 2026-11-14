/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.speech

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Key verification and available account voices, without synthesizing paid audio.
 * The official billing pages remain authoritative for actual prepaid cash balances.
 */
object PaidSpeechAccounts {
    const val ELEVEN_KEY_URL = "https://elevenlabs.io/app/settings/api-keys"
    const val ELEVEN_BILLING_URL = "https://elevenlabs.io/app/developers"
    const val INWORLD_KEY_URL = "https://platform.inworld.ai/api-keys"
    const val INWORLD_BILLING_URL = "https://platform.inworld.ai/billing"

    data class Quota(val usedCharacters: Int, val allowedCharacters: Int)

    suspend fun fetchVoices(engine: SpeechEngineId, apiKey: String): List<SpeechVoice> {
        require(apiKey.isNotBlank()) { "API key missing" }
        val url = when (engine) {
            SpeechEngineId.ELEVENLABS -> "https://api.elevenlabs.io/v2/voices?page_size=100"
            SpeechEngineId.INWORLD -> "https://api.inworld.ai/voices/v1/voices?pageSize=200"
            else -> throw IllegalArgumentException("Not an account voice provider")
        }
        val request = Request.Builder().url(url).apply {
            when (engine) {
                SpeechEngineId.ELEVENLABS -> header("xi-api-key", apiKey.trim())
                SpeechEngineId.INWORLD -> header("Authorization", "Basic ${apiKey.removePrefix("Basic ").trim()}")
                else -> Unit
            }
        }.get().build()
        return SpeechHttp.execute(request) { response ->
            SpeechHttp.requireSuccess(engine.name, response)
            val json = Json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
                ?: throw SpeechEngineException("Invalid voice catalogue")
            parseVoices(engine, json)
        }
    }

    /** A subscription quota, not a claim about ElevenLabs' money/PAYG balance. */
    suspend fun elevenQuota(apiKey: String): Quota {
        require(apiKey.isNotBlank()) { "API key missing" }
        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/user/subscription")
            .header("xi-api-key", apiKey.trim())
            .get()
            .build()
        return SpeechHttp.execute(request) { response ->
            SpeechHttp.requireSuccess("ElevenLabs", response)
            val json = Json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
                ?: throw SpeechEngineException("Invalid quota response")
            Quota(
                usedCharacters = json["character_count"]?.jsonPrimitive?.intOrNull ?: 0,
                allowedCharacters = json["character_limit"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    }

    internal fun parseVoices(engine: SpeechEngineId, root: JsonObject): List<SpeechVoice> {
        val array = root["voices"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val idField = if (engine == SpeechEngineId.ELEVENLABS) "voice_id" else "voiceId"
            val id = row[idField]?.jsonPrimitive?.content.orEmpty().trim()
            if (id.isBlank()) return@mapNotNull null
            val nameField = if (engine == SpeechEngineId.ELEVENLABS) "name" else "displayName"
            val name = row[nameField]?.jsonPrimitive?.content.orEmpty().ifBlank { id }
            val language = if (engine == SpeechEngineId.INWORLD)
                row["languageCode"]?.jsonPrimitive?.content.orEmpty() else ""
            SpeechVoice(id, if (language.isBlank()) name else "$name ($language)")
        }.distinctBy { it.id }.take(200)
    }
}
