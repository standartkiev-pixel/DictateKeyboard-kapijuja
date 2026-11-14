/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.notifications

/** Never logs original text. The caller owns temporary text and does not persist it. */
data class SpokenTextOptions(
    val skipLinks: Boolean = true,
    val skipLongNumbers: Boolean = true,
    val skipServiceMessages: Boolean = true,
    val simplifySymbols: Boolean = true,
)

object SpeechTextSanitizer {
    // Links and bare domains must not become long "h t t p ..." read-outs.
    private val links = Regex("""(?i)(?:https?://|www\.)\S+|\b[\w.+-]+\.(?:com|net|org|info|io|eu|de|fr|it|pl|uk|me|ru|tv|app)(?:/\S*)?""")
    private val longNumbers = Regex("""(?<![\p{L}\p{N}])(?:\d[\s._:/-]?){7,}(?![\p{L}\p{N}])""")
    private val hexTokens = Regex("""(?i)\b(?:[0-9a-f]{24,}|(?:sk-|ghp_|gho_|github_pat_|xai-)\S+)\b""")
    private val whitespace = Regex("""\s+""")
    private val spacesAroundPunctuation = Regex("""\s+([,.!?])""")
    private val junkSymbols = Regex("""[^\p{L}\p{N}\p{M}\s.,!?;:()'"«»„“”%-]""")
    private val serviceOnly = Regex(
        """(?i)^(?:\d+\s+)?(?:new (?:messages?|notifications?|posts?)|unread messages?|sync(?:ing| complete)?|download(?:ing| complete)?|typing(?:\.\.\.)?|checking for (?:new )?messages?|connecting|new messages? available|новых? сообщений|непрочитанных? сообщений|синхронизация|загрузка|neue nachrichten|nouveaux messages|nuovi messaggi|neue benachrichtigungen)\.?$"""
    )
    private val verification = Regex(
        """(?i)\b(?:one.time|otp|2fa|verification|verify|security code|login code|passcode|код подтверждения|одноразов|проверочн\w* код|code de vérification|codice di verifica|bestätigungscode)\b"""
    )

    /** Never read OTP/security codes, regardless of optional "long numbers" preference. */
    fun looksLikeCredential(text: String): Boolean =
        hexTokens.containsMatchIn(text) ||
            (verification.containsMatchIn(text) && text.count(Char::isDigit) >= 4)

    fun clean(raw: String, options: SpokenTextOptions): String {
        val source = raw.trim()
        if (source.isEmpty() || looksLikeCredential(source)) return ""
        if (options.skipServiceMessages && serviceOnly.matches(source)) return ""
        var text = source
        if (options.skipLinks) text = links.replace(text, " ")
        if (options.skipLongNumbers) text = longNumbers.replace(text) { match ->
            if (match.value.count(Char::isDigit) >= 7) " " else match.value
        }
        if (options.simplifySymbols) text = junkSymbols.replace(text, " ")
        text = whitespace.replace(text, " ")
        text = spacesAroundPunctuation.replace(text, "$1")
        val result = text.trim(' ', ',', ';', ':', '-')
        if (result.none(Char::isLetterOrDigit)) return ""
        if (options.skipServiceMessages && serviceOnly.matches(result)) return ""
        return result
    }
}
