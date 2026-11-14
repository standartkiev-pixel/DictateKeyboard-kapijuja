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

import dev.patrickgold.florisboard.app.FlorisPreferenceModel

/** One consistent read of the speech preferences, taken when a reading starts. */
data class SpeechSettings(
    val engine: SpeechEngineId = SpeechEngineId.ANDROID,
    /** 1.0 = normal. */
    val speed: Float = 1.0f,
    /** BCP-47 tag used when an engine needs a language and no voice implies one. */
    val language: String = "ru-RU",
    val fallbackToAndroid: Boolean = true,
    /** Extra loudness in dB on top of the media volume (0 = off). */
    val boostDb: Int = 6,
    val androidEnginePackage: String = "",
    val androidVoice: String = "",
    val androidPitch: Float = 1.0f,
    val openAiKey: String = "",
    val openAiModel: String = OpenAiRealtimeSpeechSource.DEFAULT_MODEL,
    val openAiVoice: String = "marin",
    val azureKey: String = "",
    val azureRegion: String = "",
    val azureVoice: String = "ru-RU-DmitryNeural",
    val googleKey: String = "",
    val googleModel: String = GeminiSpeechSource.DEFAULT_MODEL,
    val googleVoice: String = "Kore",
    val xaiKey: String = "",
    val xaiVoice: String = "orion",
    val elevenKey: String = "",
    val elevenModel: String = "eleven_v3",
    val elevenVoice: String = "",
    val inworldKey: String = "",
    val inworldModel: String = "inworld-tts-2",
    val inworldVoice: String = "Dennis",
) {
    val languageCode: String get() = language.substringBefore('-').lowercase().ifBlank { "ru" }

    override fun toString(): String = "SpeechSettings(engine=$engine, speed=$speed)" // never print keys

    companion object {
        /** The key of the OpenAI account in the dictation providers, or blank when there is none. */
        fun dictationOpenAiKey(prefs: FlorisPreferenceModel): String =
            prefs.dictate.providerAccounts.get()[OPENAI_PROVIDER_ID]?.apiKey.orEmpty().trim()

        private const val OPENAI_PROVIDER_ID = "openai"

        fun from(prefs: FlorisPreferenceModel): SpeechSettings {
            val s = prefs.speech
            return SpeechSettings(
                engine = s.engine.get(),
                speed = s.speedPercent.get() / 100f,
                language = s.language.get().trim().ifBlank { "ru-RU" },
                fallbackToAndroid = s.fallbackToAndroid.get(),
                boostDb = s.volumeBoostDb.get(),
                androidEnginePackage = s.androidEngine.get().trim(),
                androidVoice = s.androidVoice.get().trim(),
                androidPitch = s.androidPitchPercent.get() / 100f,
                // No separate key for reading: use the OpenAI key dictation already has.
                openAiKey = s.openAiApiKey.get().trim().ifBlank { dictationOpenAiKey(prefs) },
                openAiModel = s.openAiModel.get().trim().ifBlank { OpenAiRealtimeSpeechSource.DEFAULT_MODEL },
                openAiVoice = s.openAiVoice.get().trim().ifBlank { "marin" },
                azureKey = s.azureKey.get().trim(),
                azureRegion = s.azureRegion.get().trim(),
                azureVoice = s.azureVoice.get().trim().ifBlank { "ru-RU-DmitryNeural" },
                googleKey = s.googleApiKey.get().trim(),
                googleModel = s.googleModel.get().trim().ifBlank { GeminiSpeechSource.DEFAULT_MODEL },
                googleVoice = s.googleVoice.get().trim().ifBlank { "Kore" },
                xaiKey = s.xaiApiKey.get().trim(),
                xaiVoice = s.xaiVoice.get().trim().ifBlank { "orion" },
                elevenKey = s.elevenApiKey.get().trim(),
                elevenModel = s.elevenModel.get().trim().ifBlank { "eleven_v3" },
                elevenVoice = s.elevenVoice.get().trim(),
                inworldKey = s.inworldApiKey.get().trim(),
                inworldModel = s.inworldModel.get().trim().ifBlank { "inworld-tts-2" },
                inworldVoice = s.inworldVoice.get().trim().ifBlank { "Dennis" },
            )
        }
    }
}

/**
 * A cloud engine reduced to what the player needs: turn one piece of text into 24 kHz 16-bit mono
 * PCM, handed over as soon as it arrives. Everything provider-specific stays inside the source.
 */
interface CloudSpeechSource {
    val id: SpeechEngineId

    /** Whether [settings] have everything this source needs (key, region…), without any network. */
    fun isConfigured(settings: SpeechSettings): Boolean

    /**
     * Synthesizes [text], calling [onPcm] from the calling coroutine with each block of audio. Must
     * stop promptly, including any open connection, when the coroutine is cancelled.
     */
    suspend fun synthesize(text: String, settings: SpeechSettings, onPcm: suspend (ByteArray) -> Unit)
}

/**
 * A failure with a message safe to show the user: it never contains the spoken text or a key.
 * [billingMayApply] marks failures after the request reached the provider.
 */
class SpeechEngineException(
    message: String,
    val billingMayApply: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)
