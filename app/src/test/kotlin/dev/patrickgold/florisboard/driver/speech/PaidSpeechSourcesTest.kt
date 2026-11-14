/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class PaidSpeechSourcesTest {
    @Test
    fun `no paid synthesis is configured without user-provided credentials`() {
        assertFalse(ElevenLabsSpeechSource.isConfigured(SpeechSettings()))
        assertFalse(InworldSpeechSource.isConfigured(SpeechSettings()))
        assertFalse(ElevenLabsSpeechSource.isConfigured(SpeechSettings(elevenKey = "key")))
        assertTrue(ElevenLabsSpeechSource.isConfigured(SpeechSettings(elevenKey = "key", elevenVoice = "voice")))
        assertTrue(InworldSpeechSource.isConfigured(SpeechSettings(inworldKey = "key", inworldVoice = "Dennis")))
    }

    @Test
    fun `eleven v3 request contains text and selected model without the key`() {
        val settings = SpeechSettings(elevenKey = "private", elevenVoice = "abc", elevenModel = "eleven_v3")
        val root = Json.parseToJsonElement(ElevenLabsSpeechSource.requestJson("Привет!", settings)).toString()
        assertTrue("\"model_id\":\"eleven_v3\"" in root)
        assertTrue("\"text\":\"Привет!\"" in root)
        assertFalse("private" in root)
    }

    @Test
    fun `inworld streaming requests raw 24 kHz pcm`() {
        val settings = SpeechSettings(inworldKey = "private", inworldModel = "inworld-tts-2-flash", inworldVoice = "Ashley")
        val json = InworldSpeechSource.requestJson("Bonjour", settings)
        assertTrue("\"voiceId\":\"Ashley\"" in json)
        assertTrue("\"modelId\":\"inworld-tts-2-flash\"" in json)
        assertTrue("\"audioEncoding\":\"PCM\"" in json)
        assertTrue("\"sampleRateHertz\":24000" in json)
        assertFalse("private" in json)
    }

    @Test
    fun `account catalog supports official key and voice fields`() {
        val elevenJson = Json.parseToJsonElement(
            """{"voices":[{"voice_id":"abc123","name":"George"},{"voice_id":"abc123","name":"Repeat"},{"name":"missing"}]}"""
        ) as kotlinx.serialization.json.JsonObject
        val eleven = PaidSpeechAccounts.parseVoices(SpeechEngineId.ELEVENLABS, elevenJson)
        assertEquals(listOf(SpeechVoice("abc123", "George")), eleven)

        val inworldJson = Json.parseToJsonElement(
            """{"voices":[{"voiceId":"Dennis","displayName":"Dennis","languageCode":"en-US"},{"voiceId":"Mara","displayName":"Mara","languageCode":"ru-RU"}]}"""
        ) as kotlinx.serialization.json.JsonObject
        val voices = PaidSpeechAccounts.parseVoices(SpeechEngineId.INWORLD, inworldJson)
        assertEquals("Mara (ru-RU)", voices[1].label)
    }
}
