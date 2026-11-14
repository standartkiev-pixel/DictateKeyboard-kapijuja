/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.autoread

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoReadSwitchPolicyTest {
    private val off = AutoReadSwitchPolicy.Mode(false, false)
    private val clips = AutoReadSwitchPolicy.Mode(true, false)
    private val alerts = AutoReadSwitchPolicy.Mode(false, true)
    private val both = AutoReadSwitchPolicy.Mode(true, true)

    @Test fun `master speaker enables both when permission granted`() {
        assertEquals(both, AutoReadSwitchPolicy.toggleAll(off, notificationPermission = true))
        assertEquals(clips, AutoReadSwitchPolicy.toggleAll(off, notificationPermission = false))
    }

    @Test fun `master speaker mutes both even after individual modal disable`() {
        for (current in listOf(clips, alerts, both)) {
            assertEquals(off, AutoReadSwitchPolicy.toggleAll(current, notificationPermission = true))
        }
    }

    @Test fun `modal can disable clipboard without disabling notifications`() {
        assertEquals(alerts, AutoReadSwitchPolicy.setClipboard(both, false))
        assertTrue(AutoReadSwitchPolicy.canReadNotifications(alerts))
    }

    @Test fun `modal can disable notifications without disabling clipboard`() {
        assertEquals(clips, AutoReadSwitchPolicy.setNotifications(both, false))
        assertFalse(AutoReadSwitchPolicy.canReadNotifications(clips))
    }

    @Test fun `independent settings can recover either reading mode`() {
        assertEquals(alerts, AutoReadSwitchPolicy.setNotifications(off, true))
        assertEquals(clips, AutoReadSwitchPolicy.setClipboard(off, true))
        assertEquals(both, AutoReadSwitchPolicy.setClipboard(alerts, true))
        assertEquals(both, AutoReadSwitchPolicy.setNotifications(clips, true))
    }
}
