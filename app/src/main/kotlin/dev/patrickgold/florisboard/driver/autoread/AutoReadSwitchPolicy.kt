/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.autoread

/**
 * Smartbar: one master speaker turns BOTH automatic reading streams on/off.
 * The countdown window/settings may disable EACH stream independently.
 * Never require clipboard speech to be enabled for notification speech.
 */
internal object AutoReadSwitchPolicy {
    data class Mode(val clipboard: Boolean, val notifications: Boolean) {
        val anyEnabled: Boolean get() = clipboard || notifications
    }

    /** Turning on from OFF requires an explicit press; notification permission is checked by caller. */
    fun toggleAll(current: Mode, notificationPermission: Boolean): Mode =
        if (current.anyEnabled) Mode(clipboard = false, notifications = false)
        else Mode(clipboard = true, notifications = notificationPermission)

    fun setClipboard(current: Mode, enabled: Boolean): Mode =
        current.copy(clipboard = enabled)

    fun setNotifications(current: Mode, enabled: Boolean): Mode =
        current.copy(notifications = enabled)

    fun canReadNotifications(current: Mode): Boolean = current.notifications
}
