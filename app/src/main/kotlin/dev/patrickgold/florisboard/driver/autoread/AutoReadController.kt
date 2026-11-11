/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.driver.autoread

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.driver.speech.SpeechCoordinator
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast

/**
 * Auto-read (Driver Assistant, issue #7): when another app copies text, show a short countdown
 * with "cancel" and "turn off", then read the text with the selected speech engine.
 *
 * - Off by default; switched with the speaker toggle on the left of the Smartbar or in settings.
 * - Listens to the keyboard's existing clipboard listener ([externalClipFlow]); no second one.
 * - Nothing is sent to a cloud engine before the countdown ends, so "cancel" never costs money.
 * - Latest wins: a new copy cancels a pending countdown or a reading still in progress.
 * - Skips the lock screen, active dictation, short texts, links and anything that looks secret.
 *
 * [externalClipFlow]: dev.patrickgold.florisboard.ime.clipboard.ClipboardManager.externalClipFlow
 */
object AutoReadController {
    private const val KEY_PREFIX = "auto-"

    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var started = false
    private var job: Job? = null
    private var prompt: AutoReadPrompt? = null
    private var generation = 0L

    /** Subscribes to new clips. Called once when the app has loaded its preferences. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        appContext = app
        scope.launch {
            app.clipboardManager().value.externalClipFlow.collect { onClip(app, it) }
        }
    }

    /** Cancels the pending countdown or the auto-reading in progress; the next copy works again. */
    fun cancelCurrent() {
        generation++
        job?.cancel()
        job = null
        prompt?.hide()
        prompt = null
        if (SpeechCoordinator.activeKey()?.startsWith(KEY_PREFIX) == true) SpeechCoordinator.stop()
    }

    /** Turns auto-read off until it is switched on again, stopping whatever it is doing. */
    fun disable(context: Context) = setEnabled(context, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        if (!enabled) cancelCurrent()
        val app = context.applicationContext
        scope.launch {
            prefs.speech.autoReadEnabled.set(enabled)
            app.showShortToast(if (enabled) R.string.speech__autoread_enabled_toast else R.string.speech__autoread_disabled_toast)
        }
    }

    private fun onClip(context: Context, item: ClipboardItem) {
        if (!prefs.speech.autoReadEnabled.get()) return
        val verdict = AutoReadFilter.check(
            text = item.text,
            isSensitive = item.isSensitive || item.type != ItemType.TEXT,
            minChars = prefs.speech.autoReadMinChars.get(),
            ownCopy = DictateAccessibilityService.lastClipText,
        )
        if (verdict != AutoReadFilter.Verdict.READ) return
        if (isLocked(context)) return
        if (DictateController.state.value is DictateController.UiState.Recording) return

        cancelCurrent()
        val myGeneration = ++generation
        val text = item.text.orEmpty()
        val key = "$KEY_PREFIX$myGeneration"
        val delaySeconds = prefs.speech.autoReadDelaySec.get().coerceIn(1, 30)
        val shownPrompt = createPrompt(context, delaySeconds)
        prompt = shownPrompt
        job = scope.launch {
            try {
                val deadline = SystemClock.elapsedRealtime() + delaySeconds * 1000L
                while (true) {
                    val left = deadline - SystemClock.elapsedRealtime()
                    if (left <= 0) break
                    shownPrompt.showCountdown(((left + 999) / 1000).toInt())
                    delay(minOf(left, 200L))
                }
                if (generation != myGeneration || !prefs.speech.autoReadEnabled.get()) return@launch
                if (DictateController.state.value is DictateController.UiState.Recording) return@launch
                shownPrompt.showReading()
                SpeechCoordinator.speak(context, text, key)
                SpeechCoordinator.state.first { !SpeechCoordinator.isActive(key) }
            } finally {
                if (generation == myGeneration) {
                    shownPrompt.hide()
                    prompt = null
                    job = null
                }
            }
        }
    }

    private fun createPrompt(context: Context, delaySeconds: Int): AutoReadPrompt {
        val service = DictateAccessibilityService.overlayContext()
        return if (service != null) {
            OverlayAutoReadPrompt(
                serviceContext = service,
                onCancel = { cancelCurrent() },
                onDisable = { disable(context) },
            )
        } else {
            NotificationAutoReadPrompt(context, delaySeconds)
        }
    }

    private fun isLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
        return keyguard.isKeyguardLocked
    }
}
