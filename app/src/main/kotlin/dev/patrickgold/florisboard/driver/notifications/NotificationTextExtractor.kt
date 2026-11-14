/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.notifications

import android.app.Notification
import android.os.Build
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

        // MessagingStyle is more complete than the collapsed notification preview.
        // Android's public getMessagesFromBundleArray() exists only on API 30+.
        // Parse the documented bundles on API 26–29 instead.
        data class Entry(val sender: String, val body: String, val timestamp: Long)
        val entries: List<Entry> = runCatching {
            val array = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(array).map { msg ->
                    Entry(
                        sender = msg.senderPerson?.name?.toString()?.trim().orEmpty(),
                        body = msg.text?.toString()?.trim().orEmpty(),
                        timestamp = msg.timestamp,
                    )
                }
            } else {
                array.orEmpty().filterIsInstance<Bundle>().map { bundle ->
                    Entry(
                        sender = bundle.getCharSequence("sender")?.toString()?.trim().orEmpty(),
                        body = bundle.getCharSequence("text")?.toString()?.trim().orEmpty(),
                        timestamp = bundle.getLong("time"),
                    )
                }
            }
        }.getOrDefault(emptyList())
        val structured = entries.mapNotNull { msg ->
            if (msg.body.isBlank()) return@mapNotNull null
            val display = listOfNotNull(msg.sender.takeIf { it.isNotBlank() }, msg.body)
                .joinToString(": ").take(12_000)
            Part(
                fingerprint = (msg.sender + "\u0000" + msg.body + "\u0000" + msg.timestamp).hashCode(),
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
