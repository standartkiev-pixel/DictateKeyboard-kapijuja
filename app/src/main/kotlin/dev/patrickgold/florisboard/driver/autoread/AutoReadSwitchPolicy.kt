/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.autoread

/**
 * One compact Smartbar speaker controls two opt-in streams without surprises.
 * Clipboard OFF means all automatic reading OFF; clipboard ON does not opt into notifications.
 */
internal object AutoReadSwitchPolicy {
    data class Mode(val clipboard: Boolean, val notifications: Boolean)

    fun setClipboard(current: Mode, enabled: Boolean): Mode =
        if (!enabled) Mode(clipboard = false, notifications = false)
        else Mode(clipboard = true, notifications = current.clipboard && current.notifications)

    fun setNotifications(current: Mode, enabled: Boolean): Mode =
        Mode(clipboard = current.clipboard, notifications = current.clipboard && enabled)

    fun canReadNotifications(current: Mode): Boolean = current.clipboard && current.notifications
}
