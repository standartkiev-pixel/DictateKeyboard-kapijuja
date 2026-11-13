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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidVoiceLabelsTest {
    private val words = AndroidVoiceLabels.Words(
        numbered = { "voice $it" },
        languageDefault = "default",
        offline = "offline",
        online = "online",
    )

    private fun voice(name: String, tag: String, network: Boolean = false) =
        AndroidVoiceInfo(name, Locale.forLanguageTag(tag), network)

    private fun labels(vararg voices: AndroidVoiceInfo, preferred: String = "") =
        AndroidVoiceLabels.describe(voices.toList(), words, preferred)

    @Test
    fun `google style ids are told apart from real names`() {
        assertTrue(AndroidVoiceLabels.isTechnical("ru-ru-x-rud-local"))
        assertTrue(AndroidVoiceLabels.isTechnical("ru-RU-language"))
        assertTrue(AndroidVoiceLabels.isTechnical("en-us-x-sfg#male_1-local"))
        assertFalse(AndroidVoiceLabels.isTechnical("Anna"))
        assertFalse(AndroidVoiceLabels.isTechnical("Aleksandr+Alan"))
    }

    @Test
    fun `offline and online variants of one voice share a number`() {
        val result = labels(
            voice("ru-ru-x-rud-network", "ru-RU", network = true),
            voice("ru-ru-x-ruc-local", "ru-RU"),
            voice("ru-ru-x-rud-local", "ru-RU"),
            voice("ru-RU-language", "ru-RU"),
        )
        assertEquals(
            listOf("ru-RU-language", "ru-ru-x-ruc-local", "ru-ru-x-rud-local", "ru-ru-x-rud-network"),
            result.map { it.id },
        )
        assertTrue(result[0].label.endsWith("default · offline"))
        assertTrue(result[1].label.endsWith("voice 1 · offline"))
        assertTrue(result[2].label.endsWith("voice 2 · offline"))
        assertTrue(result[3].label.endsWith("voice 2 · online"))
    }

    @Test
    fun `the reading language comes first and real names are kept`() {
        val result = labels(
            voice("en-us-x-sfg-local", "en-US"),
            voice("Anna", "ru-RU"),
            preferred = "ru-RU",
        )
        assertEquals(listOf("Anna", "en-us-x-sfg-local"), result.map { it.id })
        assertTrue(result[0].label.endsWith("Anna · offline"))
    }
}
