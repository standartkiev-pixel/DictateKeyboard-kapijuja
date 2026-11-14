/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.notifications

/** Explicit allowlist: a notification from any other application is never read. */
enum class NotificationSource { WHATSAPP, INSTAGRAM, EMAIL, VIBER, TELEGRAM, YOUTUBE }

object NotificationSources {
    fun fromPackage(packageName: String): NotificationSource? = when (packageName) {
        "com.whatsapp", "com.whatsapp.w4b" -> NotificationSource.WHATSAPP
        "com.instagram.android" -> NotificationSource.INSTAGRAM
        "com.google.android.gm", "com.google.android.gm.lite",
        "com.microsoft.office.outlook", "com.samsung.android.email.provider",
        "com.fsck.k9", "net.thunderbird.android",
        "com.yahoo.mobile.client.android.mail" -> NotificationSource.EMAIL
        "com.viber.voip" -> NotificationSource.VIBER
        "org.telegram.messenger", "org.telegram.messenger.beta",
        "org.thunderdog.challegram" -> NotificationSource.TELEGRAM
        "com.google.android.youtube" -> NotificationSource.YOUTUBE
        else -> null
    }
}
