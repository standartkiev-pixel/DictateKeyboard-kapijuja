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

/**
 * The speech engines a user can pick for reading text aloud (Driver Assistant, issue #7).
 *
 * The Android system engine is the default and the only one that never sends text off the device
 * by itself. Every cloud engine needs the user's own key and is only used after they chose it.
 */
enum class SpeechEngineId {
    ANDROID,
    OPENAI,
    AZURE,
    GOOGLE,
    XAI,
    ELEVENLABS,
    INWORLD;

    val isCloud: Boolean get() = this != ANDROID
}

/** A selectable voice: the id sent to the provider and the label shown in settings. */
data class SpeechVoice(val id: String, val label: String)

/**
 * Static voice lists for the cloud engines whose APIs use a fixed set of named voices. Azure has a
 * per-region list endpoint and hundreds of voices, so it is a free-text field with a short list of
 * suggestions instead.
 */
object SpeechVoiceCatalog {
    val openAi = listOf(
        SpeechVoice("marin", "Marin"),
        SpeechVoice("cedar", "Cedar"),
        SpeechVoice("alloy", "Alloy"),
        SpeechVoice("ash", "Ash"),
        SpeechVoice("ballad", "Ballad"),
        SpeechVoice("coral", "Coral"),
        SpeechVoice("echo", "Echo"),
        SpeechVoice("sage", "Sage"),
        SpeechVoice("shimmer", "Shimmer"),
        SpeechVoice("verse", "Verse"),
    )

    val azureSuggestions = listOf(
        SpeechVoice("ru-RU-DmitryNeural", "Dmitry (ru-RU)"),
        SpeechVoice("ru-RU-SvetlanaNeural", "Svetlana (ru-RU)"),
        SpeechVoice("ru-RU-DariyaNeural", "Dariya (ru-RU)"),
        SpeechVoice("uk-UA-OstapNeural", "Ostap (uk-UA)"),
        SpeechVoice("uk-UA-PolinaNeural", "Polina (uk-UA)"),
        SpeechVoice("en-US-AndrewMultilingualNeural", "Andrew Multilingual (en-US)"),
        SpeechVoice("en-US-AvaMultilingualNeural", "Ava Multilingual (en-US)"),
    )

    val google = listOf(
        "Kore", "Charon", "Puck", "Zephyr", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe",
        "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib",
        "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima",
        "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat",
    ).map { SpeechVoice(it, it) }

    val elevenModels = listOf(
        SpeechVoice("eleven_v3", "Eleven v3"),
        SpeechVoice("eleven_multilingual_v2", "Multilingual v2"),
        SpeechVoice("eleven_flash_v2_5", "Flash v2.5"),
    )

    val inworldModels = listOf(
        SpeechVoice("inworld-tts-2", "TTS-2"),
        SpeechVoice("inworld-tts-2-flash", "TTS-2 Flash"),
    )

    val xai = listOf(
        "orion", "lux", "perseus", "rigel", "naksh", "atlas", "sal", "rex", "leo", "lumen", "castor",
        "ursa", "liora", "aurora", "carina", "luna", "celeste", "ara", "altair", "kepler",
        "cosmo", "iris", "helios", "zenith", "helix", "zagan", "sirius", "eve",
    ).map { SpeechVoice(it, it.replaceFirstChar(Char::uppercaseChar)) }
}
