/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.prefs

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore

/**
 * One-time correction for the old coupling between "rewording is available" and "reword every finished
 * dictation automatically".
 *
 * The feature master switch is intentionally left alone: turning it off removes the manual wand and the
 * translation prompts as well. Only the automatic post-processing gate is reset. The prompt database is
 * not edited, so any per-prompt Auto-apply choices are remembered and become active again if the user
 * later turns automatic rewording back on.
 */
object KapijujaRewordingDefaults {
    private const val MIGRATION_PREFS = "kapijuja_migrations"
    private const val KEY_AUTO_REWORD_SEPARATED_V1 = "auto_reword_separated_v1"

    suspend fun applyOnce(context: Context) {
        val marker = context.applicationContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (marker.getBoolean(KEY_AUTO_REWORD_SEPARATED_V1, false)) return

        val prefs by FlorisPreferenceStore
        prefs.dictate.autoFormattingEnabled.set(false)

        // Set the marker only after the datastore write completes so an interrupted migration retries.
        marker.edit().putBoolean(KEY_AUTO_REWORD_SEPARATED_V1, true).apply()
    }
}
