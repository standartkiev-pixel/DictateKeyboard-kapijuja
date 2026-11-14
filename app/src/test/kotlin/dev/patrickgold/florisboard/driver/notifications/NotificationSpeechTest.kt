/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationSourcesTest {
    @Test
    fun selectedAppPackages() {
        assertEquals(NotificationSource.WHATSAPP, NotificationSources.fromPackage("com.whatsapp"))
        assertEquals(NotificationSource.WHATSAPP, NotificationSources.fromPackage("com.whatsapp.w4b"))
        assertEquals(NotificationSource.INSTAGRAM, NotificationSources.fromPackage("com.instagram.android"))
        assertEquals(NotificationSource.EMAIL, NotificationSources.fromPackage("com.google.android.gm"))
        assertEquals(NotificationSource.EMAIL, NotificationSources.fromPackage("com.microsoft.office.outlook"))
        assertEquals(NotificationSource.EMAIL, NotificationSources.fromPackage("com.samsung.android.email.provider"))
        assertEquals(NotificationSource.VIBER, NotificationSources.fromPackage("com.viber.voip"))
        assertEquals(NotificationSource.TELEGRAM, NotificationSources.fromPackage("org.telegram.messenger"))
        assertEquals(NotificationSource.YOUTUBE, NotificationSources.fromPackage("com.google.android.youtube"))
    }

    @Test
    fun noUnapprovedSources() {
        assertNull(NotificationSources.fromPackage("com.google.android.apps.messaging"))
        assertNull(NotificationSources.fromPackage("net.kapijuja.dictate.debug"))
        assertNull(NotificationSources.fromPackage("com.example.bank"))
        assertNull(NotificationSources.fromPackage(""))
    }
}

class SpeechTextSanitizerTest {
    private val defaults = SpokenTextOptions()

    @Test
    fun normalSpeechPreserved() {
        assertEquals("Привет! Выгрузка завтра в 9:30.", SpeechTextSanitizer.clean(
            "Привет! Выгрузка завтра в 9:30.", defaults))
    }

    @Test
    fun urlsAreRemovedWithoutRemovingSentence() {
        val text = SpeechTextSanitizer.clean(
            "Встретимся завтра. Ссылка: https://example.com/a?x=4 Дальше поедем.", defaults)
        assertFalse("example.com" in text)
        assertTrue("Встретимся" in text)
        assertTrue("Дальше поедем" in text)
    }

    @Test
    fun linksCanBeReadWhenFilterOff() {
        val text = SpeechTextSanitizer.clean(
            "See https://example.com/foo", defaults.copy(skipLinks = false))
        assertTrue("example.com" in text)
    }

    @Test
    fun numbersLongerThanSixDigitsAreSkipped() {
        val text = SpeechTextSanitizer.clean(
            "Поставка 12345678901234 готова в 12:30.", defaults)
        assertFalse("12345678901234" in text)
        assertTrue("12:30" in text)
    }

    @Test
    fun filterCanBeTurnedOff() {
        assertTrue("1234567890" in SpeechTextSanitizer.clean(
            "Номер 1234567890 доставлен", defaults.copy(skipLongNumbers = false)))
    }

    @Test
    fun pureServiceMessagesAreNotRead() {
        assertEquals("", SpeechTextSanitizer.clean("3 new messages", defaults))
        assertEquals("", SpeechTextSanitizer.clean("Новых сообщений", defaults))
    }

    @Test
    fun verificationCodesAreAlwaysBlocked() {
        assertEquals("", SpeechTextSanitizer.clean(
            "Your verification code is 987654", defaults.copy(skipLongNumbers = false)))
    }

    @Test
    fun symbolsAreSimplified() {
        assertEquals("Спасибо за помощь!", SpeechTextSanitizer.clean(
            "Спасибо ✨✨ за помощь!", defaults))
    }
}
