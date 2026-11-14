/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.driver.ui

import android.content.Context
import android.content.res.Configuration
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import java.util.Locale

/**
 * Overlays and NotificationListenerService are created from Application/Service contexts, not
 * FlorisAppActivity's locale-specific resourcesContext. Honor Settings -> interface language
 * for strings shown over other apps, without changing Android's global language.
 */
object SpeechLocaleContext {
    fun wrap(context: Context): Context {
        val tag = runCatching { FlorisPreferenceStore.value.other.settingsLanguage.get() }
            .getOrDefault("auto")
        if (tag.isBlank() || tag == "auto") return context
        return runCatching {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            context.createConfigurationContext(config)
        }.getOrDefault(context)
    }
}
