/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import dev.patrickgold.florisboard.dictate.audio.AudioConcat
import dev.patrickgold.florisboard.dictate.audio.AudioWav
import java.io.File

/**
 * Consumes ordered long-form segment WAVs into one optional destination.
 *
 * The caller transfers source ownership to this operation. Every source is deleted in [finally], even
 * when concatenation fails, while a partial or empty destination is never returned as recoverable audio.
 * Passing no destination intentionally discards the sources without creating a merged History file.
 */
internal object LongFormAudioAssembly {
    fun consume(
        segments: List<File>,
        destination: File?,
        concatenate: (List<File>, File) -> Boolean = AudioConcat::concat,
    ): File? {
        try {
            if (destination == null || segments.isEmpty()) return null
            destination.delete()
            val merged = concatenate(segments, destination)
            return destination.takeIf {
                merged && it.exists() && it.length() > AudioWav.HEADER_SIZE
            } ?: run {
                destination.delete()
                null
            }
        } catch (_: Throwable) {
            runCatching { destination?.delete() }
            return null
        } finally {
            segments.forEach { runCatching { it.delete() } }
        }
    }
}
