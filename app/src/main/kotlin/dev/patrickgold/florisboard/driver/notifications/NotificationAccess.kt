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
import android.provider.Settings

/** No privileged permission is requested automatically; only the user can grant notification access. */
object NotificationAccess {
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
