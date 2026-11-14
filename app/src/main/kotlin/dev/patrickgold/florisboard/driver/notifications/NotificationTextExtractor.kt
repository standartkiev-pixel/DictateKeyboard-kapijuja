/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.notifications

import android.app.Notification
import android.os.Bundle

/**
 * Extracts the richest text the sender actually placed in the *notification*.
 * Does not inspect other apps' databases, conversations or private screens.
 */
internal object NotificationTextExtractor {
    data class Part(val fingerprint: Int, val text: String)
    data class Result(val parts: List<Part>, val structuredMessages: Boolean)

    fun extract(n: Notification): Result {
        val extras = n.extras ?: return Result(emptyList(), false)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val titlePrefix = title.takeIf { it.isNotBlank() && it.length <= 160 }

        // MessagingStyle is more complete than the collapsed visible notification preview.
        val messages = runCatching {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            )
        }.getOrNull().orEmpty()

        val structured = messages.mapNotNull { message ->
            val body = message.text?.toString()?.trim().orEmpty()
            if (body.isBlank()) return@mapNotNull null
            val sender = message.senderPerson?.name?.toString()?.trim().orEmpty()
                .ifBlank { message.sender?.toString()?.trim().orEmpty() }
            val display = listOfNotNull(sender.takeIf { it.isNotBlank() }, body)
                .joinToString(": ").take(12_000)
            Part(
                fingerprint = (sender + "\u0000" + body + "\u0000" + message.timestamp).hashCode(),
                text = display,
            )
        }.takeLast(6)
        if (structured.isNotEmpty()) return Result(structured, true)

        val candidates = buildList {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let(::add)
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.let(::add)
            n.tickerText?.toString()?.let(::add)
        }
        val chosen = mutableListOf<String>()
        for (candidate in candidates) {
            val clean = candidate.trim()
            if (clean.isBlank() || clean.equals(title, ignoreCase = true)) continue
            if (chosen.any { old -> old.contains(clean, ignoreCase = true) }) continue
            // If the expanded text contains shorter collapsed preview, prefer expanded only.
            chosen.removeAll { clean.contains(it, ignoreCase = true) }
            chosen.add(clean)
            if (chosen.size >= 6) break
        }
        if (chosen.isEmpty()) return Result(emptyList(), false)
        val combined = listOfNotNull(titlePrefix, chosen.joinToString(". ")).joinToString(". ")
            .take(15_000)
        return Result(listOf(Part(combined.hashCode(), combined)), false)
    }
}
