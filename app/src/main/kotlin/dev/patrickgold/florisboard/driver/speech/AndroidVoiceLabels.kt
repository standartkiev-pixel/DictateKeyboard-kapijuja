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

import android.content.Context
import dev.patrickgold.florisboard.R
import java.util.Locale

/** What Android tells us about one voice of a text-to-speech engine. */
data class AndroidVoiceInfo(
    val name: String,
    val locale: Locale,
    val needsNetwork: Boolean,
)

/**
 * Turns the voice ids of an Android engine into labels a person can choose from.
 *
 * Google's engine names its voices like `ru-ru-x-rud-local`, `ru-ru-x-rud-network` and
 * `ru-RU-language`, and lists every language it has. Shown raw, that is a long list of near
 * duplicates. Here the reading language comes first, the same voice gets the same number in its
 * offline and online variants ("voice 2 · offline"), and the language default is called that.
 * Engines whose voices already carry real names (RHVoice: "Anna") keep them.
 *
 * Android does not say whether a voice is male or female, so the label cannot either; the settings
 * screen has a "listen" button next to each voice instead.
 */
object AndroidVoiceLabels {
    /** The words a label is built from, so the logic can be tested without Android resources. */
    data class Words(
        val numbered: (Int) -> String,
        val languageDefault: String,
        val offline: String,
        val online: String,
    )

    fun describe(context: Context, voices: List<AndroidVoiceInfo>, preferredLanguage: String = ""): List<SpeechVoice> =
        describe(
            voices,
            Words(
                numbered = { context.getString(R.string.speech__android_voice_numbered, it) },
                languageDefault = context.getString(R.string.speech__android_voice_default),
                offline = context.getString(R.string.speech__android_voice_offline),
                online = context.getString(R.string.speech__android_voice_online),
            ),
            preferredLanguage,
        )

    fun describe(voices: List<AndroidVoiceInfo>, words: Words, preferredLanguage: String = ""): List<SpeechVoice> {
        val preferred = preferredLanguage.substringBefore('-').substringBefore('_').lowercase()
        // The same voice in its offline and online variant shares one number within its locale.
        val numbers = voices
            .filter { isTechnical(it.name) && !isLanguageDefault(it.name) }
            .groupBy { it.locale.toLanguageTag() }
            .mapValues { (_, list) -> list.map { baseOf(it.name) }.distinct().sorted() }
        return voices
            .sortedWith(
                compareBy<AndroidVoiceInfo>(
                    { if (preferred.isNotEmpty() && it.locale.language.lowercase() == preferred) 0 else 1 },
                    { it.locale.getDisplayName(Locale.getDefault()) },
                    { if (isLanguageDefault(it.name)) 0 else 1 },
                    { numbers[it.locale.toLanguageTag()]?.indexOf(baseOf(it.name)) ?: 0 },
                    { if (it.needsNetwork) 1 else 0 },
                    { it.name },
                )
            )
            .map { voice ->
                val what = when {
                    isLanguageDefault(voice.name) -> words.languageDefault
                    isTechnical(voice.name) -> {
                        val index = numbers[voice.locale.toLanguageTag()]?.indexOf(baseOf(voice.name)) ?: -1
                        if (index >= 0) words.numbered(index + 1) else voice.name
                    }
                    else -> voice.name
                }
                val where = if (voice.needsNetwork) words.online else words.offline
                val language = voice.locale.getDisplayName(Locale.getDefault()).ifBlank { voice.locale.toLanguageTag() }
                SpeechVoice(voice.name, "$language · $what · $where")
            }
    }

    /** `ru-ru-x-rud-local` and `ru-ru-x-rud-network` are the same voice: `ru-ru-x-rud`. */
    internal fun baseOf(name: String): String =
        name.lowercase().removeSuffix("-local").removeSuffix("-network")

    internal fun isLanguageDefault(name: String): Boolean = name.lowercase().endsWith("-language")

    /** An id made of language codes rather than a name someone gave the voice. */
    internal fun isTechnical(name: String): Boolean = TechnicalName.matches(name.lowercase())

    private val TechnicalName = Regex("^[a-z]{2,3}[-_][a-z0-9]{2,4}[-_](x[-_][a-z0-9]+([-_#][a-z0-9_#-]+)?|language)$")
}
