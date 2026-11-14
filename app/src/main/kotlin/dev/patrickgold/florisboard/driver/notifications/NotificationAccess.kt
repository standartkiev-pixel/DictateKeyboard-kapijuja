/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.provider.Settings

/** No privileged permission is requested automatically; only the user can grant notification access. */
object NotificationAccess {
    private val prefs by FlorisPreferenceStore
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private var lastRebindMillis: Long = 0L
    private const val REBIND_COOLDOWN_MS = 30_000L

    /** A granted permission is not the same as an active Android service binding. */
    fun markConnected() { _connected.value = true }
    fun markDisconnected() { _connected.value = false }

    /**
     * Re-request Android's service binding after a process restart or listener disconnect.
     * Unlike a foreground-service keepalive this costs no ongoing battery and it does not
     * change user preferences or request new permissions. Android may still restrict delivery.
     */
    @Synchronized
    fun requestRecovery(context: Context, force: Boolean = false): Boolean {
        if (_connected.value || !prefs.speech.autoReadEnabled.get() ||
            !prefs.speech.notificationReadEnabled.get() || !granted(context)) return false
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRebindMillis < REBIND_COOLDOWN_MS) return false
        lastRebindMillis = now
        return runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, KapijujaNotificationListenerService::class.java)
            )
            true
        }.getOrDefault(false)
    }

    fun granted(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val component = ComponentName(context, KapijujaNotificationListenerService::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            manager.isNotificationListenerAccessGranted(component)
        } else {
            val enabled = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ).orEmpty()
            enabled.split(':').any { it == component.flattenToString() }
        }
    }

    fun openSettings(context: Context) {
        val component = ComponentName(context, KapijujaNotificationListenerService::class.java)
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(
            Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
        ).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        else null
        val all = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val intent = detail?.takeIf { it.resolveActivity(context.packageManager) != null } ?: all
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
