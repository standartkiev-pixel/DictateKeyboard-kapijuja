/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.notifications

import android.app.KeyguardManager
import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.LinkedHashMap
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.driver.autoread.AutoReadController

/**
 * Opt-in read-aloud of selected applications' *posted notifications*.
 *
 * Android alone supplies these notifications after the user grants Notification Access.
 * We never retain original text in a database, call APIs from the source applications,
 * enumerate active notifications on reconnect, or store notification bodies in logs.
 */
class KapijujaNotificationListenerService : NotificationListenerService() {
    private val prefs by FlorisPreferenceStore
    private val seen = object : LinkedHashMap<String, LinkedHashSet<Int>>(128, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, LinkedHashSet<Int>>?
        ): Boolean = size > 150
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (!prefs.speech.notificationReadEnabled.get()) return
            val source = NotificationSources.fromPackage(sbn.packageName) ?: return
            if (!isSourceEnabled(source)) return
            val notification = sbn.notification ?: return
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
                notification.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
                notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
            ) return
            if (notification.visibility == Notification.VISIBILITY_SECRET) return
            if (notification.category in setOf(
                    Notification.CATEGORY_CALL,
                    Notification.CATEGORY_SERVICE,
                    Notification.CATEGORY_TRANSPORT,
                    Notification.CATEGORY_PROGRESS
                )
            ) return
            if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return

            val extracted = NotificationTextExtractor.extract(notification)
            if (extracted.parts.isEmpty()) return

            val key = sbn.key
            val known = seen[key] ?: linkedSetOf()
            val fresh = extracted.parts.filter { it.fingerprint !in known }
            if (fresh.isEmpty()) return
            // A notification may be updated with one additional message. Read only new messages
            // instead of announcing the whole history on every new post.
            extracted.parts.forEach { known.add(it.fingerprint) }
            if (known.size > 40) {
                val retained = known.takeLast(40)
                known.clear()
                known.addAll(retained)
            }
            seen[key] = known

            val options = SpokenTextOptions(
                skipLinks = prefs.speech.skipSpokenLinks.get(),
                skipLongNumbers = prefs.speech.skipSpokenLongNumbers.get(),
                skipServiceMessages = prefs.speech.skipSpokenServiceMessages.get(),
                simplifySymbols = prefs.speech.simplifySpokenSymbols.get(),
            )
            // Filter each message separately: a single OTP in a group chat must not
            // prevent the other legitimate new messages from being spoken.
            val spokenText = fresh.map { SpeechTextSanitizer.clean(it.text, options) }
                .filter(String::isNotBlank)
                .joinToString(". ")
                .take(60_000)
            if (spokenText.isBlank()) return
            AutoReadController.onNotification(applicationContext, spokenText)
        } catch (e: Exception) {
            // No notification bodies or identifying titles are included in failure logs.
            Log.w("KapijujaNotification", "Notification processing failed: \${e.javaClass.simpleName}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
    }

    override fun onListenerDisconnected() {
        seen.clear()
        // The Android system (not a polling timer) owns binding/rebinding this listener.
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(this, KapijujaNotificationListenerService::class.java))
        }
    }

    private fun isSourceEnabled(source: NotificationSource): Boolean = when (source) {
        NotificationSource.WHATSAPP -> prefs.speech.notifyWhatsApp.get()
        NotificationSource.INSTAGRAM -> prefs.speech.notifyInstagram.get()
        NotificationSource.EMAIL -> prefs.speech.notifyEmail.get()
        NotificationSource.VIBER -> prefs.speech.notifyViber.get()
        NotificationSource.TELEGRAM -> prefs.speech.notifyTelegram.get()
        NotificationSource.YOUTUBE -> prefs.speech.notifyYouTube.get()
    }
}
