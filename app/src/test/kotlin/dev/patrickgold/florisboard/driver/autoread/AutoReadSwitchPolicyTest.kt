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
    private val both = AutoReadSwitchPolicy.Mode(true, true)

    @Test fun `short tap enables only clipboard reading from off`() {
        assertEquals(clips, AutoReadSwitchPolicy.setClipboard(off, true))
    }

    @Test fun `short tap disables BOTH reading streams`() {
        assertEquals(off, AutoReadSwitchPolicy.setClipboard(both, false))
        assertEquals(off, AutoReadSwitchPolicy.setClipboard(clips, false))
    }

    @Test fun `long press separately toggles notification reading only when clipboard on`() {
        assertEquals(both, AutoReadSwitchPolicy.setNotifications(clips, true))
        assertEquals(clips, AutoReadSwitchPolicy.setNotifications(both, false))
        assertEquals(off, AutoReadSwitchPolicy.setNotifications(off, true))
    }

    @Test fun `notifications never resume from stale restored preference when clipboard reenabled`() {
        val stale = AutoReadSwitchPolicy.Mode(false, true)
        assertEquals(clips, AutoReadSwitchPolicy.setClipboard(stale, true))
        assertFalse(AutoReadSwitchPolicy.canReadNotifications(stale))
        assertTrue(AutoReadSwitchPolicy.canReadNotifications(both))
    }
}
