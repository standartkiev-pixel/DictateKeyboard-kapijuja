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

import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Microsoft Azure Speech, REST text-to-speech with the user's key and region. Asks for raw 24 kHz
 * PCM, which Azure sends as a chunked stream: the first audio reaches the player while the rest of
 * the sentence is still being synthesized.
 */
object AzureSpeechSource : CloudSpeechSource {
    override val id = SpeechEngineId.AZURE

    override fun isConfigured(settings: SpeechSettings): Boolean =
        settings.azureKey.isNotBlank() && normalizedRegion(settings.azureRegion).isNotEmpty()

    override suspend fun synthesize(text: String, settings: SpeechSettings, onPcm: suspend (ByteArray) -> Unit) {
        val region = normalizedRegion(settings.azureRegion)
        val request = Request.Builder()
            .url("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
            .header("Ocp-Apim-Subscription-Key", settings.azureKey)
            .header("X-Microsoft-OutputFormat", "raw-24khz-16bit-mono-pcm")
            .header("User-Agent", "DictateKapijuja")
            .post(ssml(text, settings).toRequestBody("application/ssml+xml; charset=utf-8".toMediaType()))
            .build()
        SpeechHttp.execute(request) { response ->
            SpeechHttp.requireSuccess("Azure", response)
            val aligner = PcmFrameAligner()
            SpeechHttp.streamBody(response) { bytes, n -> aligner.accept(bytes, n)?.let { onPcm(it) } }
            aligner.finish()?.let { onPcm(it) }
        }
    }

    internal fun ssml(text: String, settings: SpeechSettings): String {
        val voice = settings.azureVoice
        val lang = voiceLocale(voice) ?: settings.language
        val ratePercent = ((settings.speed - 1f) * 100f).toInt().coerceIn(-50, 100)
        val rate = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${xml(lang)}'>" +
            "<voice name='${xml(voice)}'><prosody rate='$rate'>${xml(text)}</prosody></voice></speak>"
    }

    /** "ru-RU-DmitryNeural" → "ru-RU". */
    internal fun voiceLocale(voice: String): String? =
        Regex("^([a-z]{2,3}-[A-Z]{2})-").find(voice)?.groupValues?.get(1)

    /** Accepts "West Europe", "westeurope" or a pasted endpoint host and returns "westeurope". */
    internal fun normalizedRegion(raw: String): String {
        val trimmed = raw.trim().lowercase(Locale.ROOT)
            .removePrefix("https://")
            .substringBefore('.')
        return trimmed.filter { it.isLetterOrDigit() }
    }

    private fun xml(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c >= ' ' || c == '\n' || c == '\t') append(c)
            }
        }
    }
}
