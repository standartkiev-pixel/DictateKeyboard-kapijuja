/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.driver.autoread

/**
 * Decides whether a clip another app just copied may be read aloud automatically. Pure, so the
 * rules are unit-tested. Best effort by design: it catches the obvious secrets (one-time codes,
 * tokens, password-like strings), it cannot recognise every one.
 */
object AutoReadFilter {
    enum class Verdict { READ, EMPTY, SENSITIVE, TOO_SHORT, OWN_COPY, LOOKS_SECRET, LINK }

    fun check(
        text: String?,
        isSensitive: Boolean,
        minChars: Int,
        ownCopy: String? = null,
    ): Verdict {
        val trimmed = text?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> Verdict.EMPTY
            isSensitive -> Verdict.SENSITIVE
            ownCopy != null && ownCopy.trim() == trimmed -> Verdict.OWN_COPY
            trimmed.length < minChars -> Verdict.TOO_SHORT
            looksLikeLink(trimmed) -> Verdict.LINK
            looksLikeSecret(trimmed) -> Verdict.LOOKS_SECRET
            else -> Verdict.READ
        }
    }

    internal fun looksLikeLink(text: String): Boolean =
        !text.any { it.isWhitespace() } && LinkRegex.matches(text)

    internal fun looksLikeSecret(text: String): Boolean {
        // One-time codes, card and account numbers: digits with optional spaces or dashes only.
        if (DigitsOnly.matches(text)) return true
        if (text.any { it.isWhitespace() }) return false
        if (KnownTokenPrefixes.any { text.startsWith(it) }) return true
        if (text.length !in 6..128) return false
        var classes = 0
        if (text.any { it.isLowerCase() }) classes++
        if (text.any { it.isUpperCase() }) classes++
        if (text.any { it.isDigit() }) classes++
        if (text.any { !it.isLetterOrDigit() }) classes++
        // A single "word" mixing three or more character classes reads like a password or key.
        return classes >= 3
    }

    private val DigitsOnly = Regex("^[0-9][0-9 \\-]*[0-9]$")
    private val LinkRegex = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    private val KnownTokenPrefixes = listOf("sk-", "AIza", "ghp_", "gho_", "github_pat_", "xai-", "eyJ")
}
