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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeechTextChunkerTest {
    @Test
    fun `blank text gives no pieces`() {
        assertEquals(emptyList(), SpeechTextChunker.split("  \n\t "))
    }

    @Test
    fun `short text is one piece with whitespace collapsed`() {
        assertEquals(listOf("Привет,  мир!".replace("  ", " ")), SpeechTextChunker.split("  Привет,   мир!  "))
    }

    @Test
    fun `first piece is short and breaks at a sentence end`() {
        val sentence = "Это предложение номер один для проверки. "
        val text = sentence.repeat(30)
        val pieces = SpeechTextChunker.split(text, firstChunkMax = 100, chunkMax = 400)
        assertTrue(pieces.first().length <= 100)
        assertTrue(pieces.first().endsWith("."))
        pieces.drop(1).forEach { assertTrue(it.length <= 400) }
        assertEquals(SpeechTextChunker.normalize(text), pieces.joinToString(" "))
    }

    @Test
    fun `no word is cut when a space exists`() {
        val text = (1..200).joinToString(" ") { "слово$it" }
        val pieces = SpeechTextChunker.split(text, firstChunkMax = 50, chunkMax = 120)
        val words = text.split(" ").toSet()
        pieces.forEach { piece -> piece.split(" ").forEach { assertTrue(it in words, "cut word: $it") } }
        assertEquals(text, pieces.joinToString(" "))
    }

    @Test
    fun `a single endless token is hard cut`() {
        val text = "x".repeat(250)
        val pieces = SpeechTextChunker.split(text, firstChunkMax = 100, chunkMax = 100)
        assertEquals(listOf(100, 100, 50), pieces.map { it.length })
    }

    @Test
    fun `line breaks are kept and used as breaks`() {
        val text = "Первая строка без точки\nВторая строка без точки\nТретья"
        assertEquals("Первая строка без точки\nВторая строка без точки\nТретья", SpeechTextChunker.normalize(text + "\n\n"))
        val pieces = SpeechTextChunker.split(text, firstChunkMax = 29, chunkMax = 29)
        assertEquals(listOf("Первая строка без точки", "Вторая строка без точки", "Третья"), pieces)
    }
}

class PcmAudioTest {
    private fun wav(dataBytes: ByteArray, extraChunk: Boolean = false): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        out.write("RIFF".toByteArray()); int(36 + dataBytes.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); out.write(ByteArray(16))
        if (extraChunk) { out.write("LIST".toByteArray()); int(4); out.write(ByteArray(4)) }
        out.write("data".toByteArray()); int(dataBytes.size); out.write(dataBytes)
        return out.toByteArray()
    }

    @Test
    fun `raw pcm has no header`() {
        assertEquals(0, PcmAudio.wavHeaderLength(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)))
    }

    @Test
    fun `wav header length is found, also behind extra chunks`() {
        assertEquals(44, PcmAudio.wavHeaderLength(wav(ByteArray(10))))
        assertEquals(56, PcmAudio.wavHeaderLength(wav(ByteArray(10), extraChunk = true)))
        assertEquals(-1, PcmAudio.wavHeaderLength(wav(ByteArray(10)).copyOf(30)))
    }

    @Test
    fun `aligner strips a header split across reads and keeps frames whole`() {
        val samples = ByteArray(11) { (it + 1).toByte() }
        val file = wav(samples)
        val aligner = PcmFrameAligner()
        val out = java.io.ByteArrayOutputStream()
        // Feed in awkward 7-byte reads.
        var i = 0
        while (i < file.size) {
            val n = minOf(7, file.size - i)
            aligner.accept(file.copyOfRange(i, i + n))?.let { assertEquals(0, it.size % 2); out.write(it) }
            i += n
        }
        aligner.finish()?.let { out.write(it) }
        // 11 bytes of data: the last odd byte is never played as half a sample.
        assertContentEquals(samples.copyOf(10), out.toByteArray())
    }

    @Test
    fun `aligner passes raw pcm through`() {
        val aligner = PcmFrameAligner()
        val first = aligner.accept(byteArrayOf(10, 20, 30))
        assertContentEquals(byteArrayOf(10, 20), first)
        assertContentEquals(byteArrayOf(30, 40), aligner.accept(byteArrayOf(40)))
        assertNull(aligner.finish())
    }

    @Test
    fun `bytes for millis are whole frames`() {
        assertEquals(48_000, PcmAudio.bytesForMillis(1000))
        assertEquals(0, PcmAudio.bytesForMillis(7, sampleRate = 1001) % 2)
    }
}

class SpeechRequestTest {
    @Test
    fun `azure ssml escapes text and derives language from voice`() {
        val settings = SpeechSettings(azureVoice = "uk-UA-PolinaNeural", speed = 1.25f)
        val ssml = AzureSpeechSource.ssml("A & B <c> 'd'", settings)
        assertTrue("xml:lang='uk-UA'" in ssml)
        assertTrue("A &amp; B &lt;c&gt; &apos;d&apos;" in ssml)
        assertTrue("rate='+25%'" in ssml)
    }

    @Test
    fun `azure region is normalized`() {
        assertEquals("westeurope", AzureSpeechSource.normalizedRegion(" West Europe "))
        assertEquals("eastus", AzureSpeechSource.normalizedRegion("https://eastus.tts.speech.microsoft.com"))
    }

    @Test
    fun `openai session asks for 24 kHz pcm audio only`() {
        val json = OpenAiRealtimeSpeechSource.sessionUpdate(SpeechSettings(openAiVoice = "cedar"))
        assertTrue("\"type\":\"session.update\"" in json)
        assertTrue("\"output_modalities\":[\"audio\"]" in json)
        assertTrue("\"rate\":24000" in json)
        assertTrue("\"voice\":\"cedar\"" in json)
    }

    @Test
    fun `gemini audio parts are concatenated`() {
        val a = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2))
        val b = java.util.Base64.getEncoder().encodeToString(byteArrayOf(3, 4))
        val body = """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;rate=24000","data":"$a"}},{"inlineData":{"data":"$b"}}]}}]}"""
        assertContentEquals(byteArrayOf(1, 2, 3, 4), GeminiSpeechSource.extractPcm(body))
        assertNull(GeminiSpeechSource.extractPcm("""{"candidates":[]}"""))
    }

    @Test
    fun `settings never print keys`() {
        val text = SpeechSettings(openAiKey = "sk-secret", azureKey = "az-secret").toString()
        assertTrue("secret" !in text)
    }
}
