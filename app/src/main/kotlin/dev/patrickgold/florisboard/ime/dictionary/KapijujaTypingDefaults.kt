/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore

/**
 * One-time Kapijuja default migration for personal typing suggestions.
 *
 * The learning engine itself already has the important privacy and quality gates: it ignores incognito
 * and password sessions and only learns words typed key-by-key. Older builds shipped the engine disabled,
 * which meant its frequency/recency ranking (including e-mail/address-like tokens) never had data to use.
 * This migration enables the three pieces that must be on together, once, and then gets out of the way;
 * afterwards the normal settings remain authoritative and the user can turn any of them back off.
 *
 * A private SharedPreferences marker is used deliberately instead of adding another field to the large
 * JetPref model solely for a one-shot migration.
 */
object KapijujaTypingDefaults {
    private const val MIGRATION_PREFS = "kapijuja_migrations"
    private const val KEY_PERSONAL_TYPING_V1 = "personal_typing_v1"

    suspend fun applyOnce(context: Context) {
        val marker = context.applicationContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (marker.getBoolean(KEY_PERSONAL_TYPING_V1, false)) return

        val prefs by FlorisPreferenceStore
        prefs.dictionary.enableFlorisUserDictionary.set(true)
        prefs.suggestion.enabled.set(true)
        prefs.suggestion.learnTypedWords.set(true)

        // Mark only after all three writes complete. A process death between writes simply retries the
        // idempotent set(true) operations on next start instead of leaving learning half-enabled.
        marker.edit().putBoolean(KEY_PERSONAL_TYPING_V1, true).apply()
    }
}
