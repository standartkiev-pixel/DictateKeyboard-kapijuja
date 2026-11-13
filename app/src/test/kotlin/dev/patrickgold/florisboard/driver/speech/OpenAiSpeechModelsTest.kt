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

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAiSpeechModelsTest {
    @Test
    fun `keeps only realtime speech models with the default first`() {
        val json = """
            {"object":"list","data":[
              {"id":"gpt-5","object":"model"},
              {"id":"gpt-realtime","object":"model"},
              {"id":"gpt-4o-realtime-transcribe","object":"model"},
              {"id":"${OpenAiRealtimeSpeechSource.DEFAULT_MODEL}","object":"model"},
              {"id":"gpt-realtime","object":"model"},
              {"id":"whisper-1","object":"model"}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(OpenAiRealtimeSpeechSource.DEFAULT_MODEL, "gpt-realtime"),
            OpenAiSpeechModels.parse(json),
        )
    }

    @Test
    fun `an answer that is not a model list gives no models`() {
        assertEquals(emptyList(), OpenAiSpeechModels.parse("not json"))
        assertEquals(emptyList(), OpenAiSpeechModels.parse("""{"error":{"message":"bad key"}}"""))
    }
}
