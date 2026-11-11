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

import dev.patrickgold.florisboard.driver.autoread.AutoReadFilter.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoReadFilterTest {
    private fun check(text: String?, sensitive: Boolean = false, min: Int = 18, own: String? = null) =
        AutoReadFilter.check(text, sensitive, min, own)

    @Test
    fun `an ordinary message is read`() {
        assertEquals(Verdict.READ, check("Привет! Буду через десять минут, жди у входа."))
    }

    @Test
    fun `texts shorter than the minimum are skipped`() {
        assertEquals(Verdict.TOO_SHORT, check("Ок, еду"))
        assertEquals(Verdict.TOO_SHORT, check("   ровно 17 знаков  ".padEnd(5)))
        assertEquals(Verdict.READ, check("ровно восемнадцать", min = 18))
    }

    @Test
    fun `blank, sensitive and own copies are skipped`() {
        assertEquals(Verdict.EMPTY, check("   "))
        assertEquals(Verdict.EMPTY, check(null))
        assertEquals(Verdict.SENSITIVE, check("Это длинное, но секретное сообщение", sensitive = true))
        assertEquals(Verdict.OWN_COPY, check("Я продиктовал этот текст сам себе", own = "Я продиктовал этот текст сам себе "))
    }

    @Test
    fun `codes, tokens and passwords are skipped`() {
        assertEquals(Verdict.LOOKS_SECRET, check("4276 1600 1234 5678", min = 4))
        assertEquals(Verdict.LOOKS_SECRET, check("123-456", min = 4))
        assertEquals(Verdict.LOOKS_SECRET, check("sk-proj-abcdefghijklmnopqrstuvwxyz"))
        assertEquals(Verdict.LOOKS_SECRET, check("Tr0ub4dor&3xyzQ!wer"))
    }

    @Test
    fun `links are skipped but text with a link is read`() {
        assertEquals(Verdict.LINK, check("https://example.com/some/long/path?x=1"))
        assertEquals(Verdict.READ, check("Посмотри вот это: https://example.com/a"))
    }

    @Test
    fun `a long single lowercase word is not taken for a password`() {
        assertEquals(Verdict.READ, check("превысокомногорассмотрительствующий"))
    }
}
