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
 * Splits text into pieces a speech engine is asked for one at a time.
 *
 * The first piece is deliberately short so the first sound comes quickly: a cloud engine answers a
 * 200-character request much sooner than a 2000-character one. Later pieces are larger, because by
 * then the previous piece is playing and hides the request time. Breaks prefer, in this order, the
 * end of a sentence, a line break, a clause mark and a space; a piece is only cut mid-word when it
 * has no space at all within the limit.
 */
object SpeechTextChunker {
    const val DEFAULT_FIRST_CHUNK = 220
    const val DEFAULT_CHUNK = 900

    fun split(
        text: String,
        firstChunkMax: Int = DEFAULT_FIRST_CHUNK,
        chunkMax: Int = DEFAULT_CHUNK,
    ): List<String> {
        require(firstChunkMax > 0 && chunkMax > 0)
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            val limit = if (result.isEmpty()) firstChunkMax else chunkMax
            val remaining = normalized.length - start
            if (remaining <= limit) {
                normalized.substring(start).trim().takeIf { it.isNotEmpty() }?.let(result::add)
                break
            }
            val end = findBreak(normalized, start, start + limit)
            normalized.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            start = end
            while (start < normalized.length && normalized[start].isWhitespace()) start++
        }
        return result
    }

    /** Collapses runs of spaces and tabs, keeps single line breaks, drops blank lines' padding. */
    internal fun normalize(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /** Exclusive end index of the best break in `text[start, hardEnd)`. */
    private fun findBreak(text: String, start: Int, hardEnd: Int): Int {
        // Never accept a break in the first third: a tiny piece followed by a big one sounds choppy.
        val minEnd = start + (hardEnd - start) / 3
        lastIndexIn(text, minEnd, hardEnd) { i ->
            text[i] in SentenceEnds && (i + 1 >= text.length || text[i + 1].isWhitespace())
        }?.let { return it + 1 }
        lastIndexIn(text, minEnd, hardEnd) { i -> text[i] == '\n' }?.let { return it }
        lastIndexIn(text, minEnd, hardEnd) { i ->
            text[i] in ClauseMarks && (i + 1 >= text.length || text[i + 1].isWhitespace())
        }?.let { return it + 1 }
        lastIndexIn(text, start + 1, hardEnd) { i -> text[i].isWhitespace() }?.let { return it }
        return hardEnd
    }

    private inline fun lastIndexIn(text: String, from: Int, until: Int, predicate: (Int) -> Boolean): Int? {
        var i = minOf(until, text.length) - 1
        while (i >= from) {
            if (predicate(i)) return i
            i--
        }
        return null
    }

    private val SentenceEnds = setOf('.', '!', '?', '…', '。', '！', '？')
    private val ClauseMarks = setOf(',', ';', ':', '—', '–', ')')
}
