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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.SystemClock
import android.util.Log
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.driver.speech.SpeechCoordinator
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
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
    private const val TAG = "KapijujaAutoRead"

    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var subscriptionJob: Job? = null
    private var job: Job? = null
    private var prompt: AutoReadPrompt? = null
    private var generation = 0L

    // Only touched on the main thread: by the countdown coroutine and by the window's buttons.
    private var countingDown = false
    private var countdownPaused = false

    /** The last text this app itself put on the clipboard; never read back to the user. */
    @Volatile
    private var ownCopy: String? = null

    /** Call right before the app copies [text] itself (a transcript, a history entry). */
    fun noteOwnCopy(text: String) {
        ownCopy = text
    }

    /** Self-healing subscription; a collector failure must never leave the enabled switch inert. */
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (subscriptionJob?.isActive == true) return
        subscriptionJob = scope.launch {
            while (isActive) {
                try {
                    app.clipboardManager().value.externalClipFlow.collect { onClip(app, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Clipboard subscription failed; restarting", e)
                    delay(500)
                }
            }
        }
    }

    /** Event-driven recovery after user opens the keyboard; no background polling. */
    fun onKeyboardShown(context: Context) {
        start(context)
        if (prefs.speech.autoReadEnabled.get()) {
            context.clipboardManager().value.refreshClipboardObservation()
        }
    }

    /**
     * The left button of the window: holds the countdown, or the reading once it has started, and
     * continues it on the next press.
     */
    fun togglePause() {
        val context = appContext ?: return
        if (countingDown) {
            countdownPaused = !countdownPaused
        } else if (SpeechCoordinator.activeKey()?.startsWith(KEY_PREFIX) == true) {
            SpeechCoordinator.togglePause(context)
        }
    }

    /** Cancels the pending countdown or the auto-reading in progress; the next copy works again. */
    fun cancelCurrent() {
        generation++
        countingDown = false
        countdownPaused = false
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
        if (enabled) start(app)
        scope.launch {
            prefs.speech.autoReadEnabled.set(enabled)
            if (enabled && !canShowWindow(app)) {
                // Without a way to draw the countdown window the user would hear text with no chance
                // to cancel it first, so ask for the permission right away.
                app.showShortToast(R.string.speech__autoread_needs_overlay_toast)
                openOverlayPermissionSettings(app)
            } else {
                app.showShortToast(if (enabled) R.string.speech__autoread_enabled_toast else R.string.speech__autoread_disabled_toast)
            }
        }
    }

    /** What happened to the most recent clip another app copied, for the settings status line. */
    data class LastClip(val atMillis: Long, val outcome: Outcome, val length: Int)

    enum class Outcome { READ, DISABLED, EMPTY, SENSITIVE, TOO_SHORT, OWN_COPY, LOOKS_SECRET, LINK, LOCKED, DICTATING }

    private val _lastClip = MutableStateFlow<LastClip?>(null)
    val lastClip: StateFlow<LastClip?> = _lastClip.asStateFlow()

    private fun record(outcome: Outcome, item: ClipboardItem) {
        _lastClip.value = LastClip(System.currentTimeMillis(), outcome, item.text?.trim()?.length ?: 0)
    }

    private fun onClip(context: Context, item: ClipboardItem) {
        if (!prefs.speech.autoReadEnabled.get()) return record(Outcome.DISABLED, item)
        val verdict = AutoReadFilter.check(
            text = item.text,
            isSensitive = item.isSensitive || item.type != ItemType.TEXT,
            minChars = prefs.speech.autoReadMinChars.get(),
            ownCopy = ownCopy,
        )
        if (verdict != AutoReadFilter.Verdict.READ) return record(Outcome.valueOf(verdict.name), item)
        if (isLocked(context)) return record(Outcome.LOCKED, item)
        if (DictateController.state.value is DictateController.UiState.Recording) return record(Outcome.DICTATING, item)
        record(Outcome.READ, item)

        cancelCurrent()
        val myGeneration = ++generation
        val text = item.text.orEmpty()
        val key = "$KEY_PREFIX$myGeneration"
        val delaySeconds = prefs.speech.autoReadDelaySec.get().coerceIn(1, 30)
        val shownPrompt = createPrompt(context)
        prompt = shownPrompt
        job = scope.launch {
            try {
                // A paused countdown simply stops losing time, so the remaining time is counted
                // down here instead of being compared with a fixed deadline.
                var left = delaySeconds * 1000L
                var last = SystemClock.elapsedRealtime()
                countingDown = true
                countdownPaused = false
                while (left > 0) {
                    shownPrompt.showCountdown(((left + 999) / 1000).toInt(), countdownPaused)
                    delay(minOf(left, 200L))
                    val now = SystemClock.elapsedRealtime()
                    if (!countdownPaused) left -= now - last
                    last = now
                }
                countingDown = false
                if (generation != myGeneration || !prefs.speech.autoReadEnabled.get()) return@launch
                if (DictateController.state.value is DictateController.UiState.Recording) return@launch
                SpeechCoordinator.speak(context, text, key)
                SpeechCoordinator.state
                    .takeWhile { SpeechCoordinator.keyOf(it) == key }
                    .collect { shownPrompt.showReading(paused = it is SpeechCoordinator.State.Paused) }
            } finally {
                if (generation == myGeneration) {
                    countingDown = false
                    shownPrompt.hide()
                    prompt = null
                    job = null
                }
            }
        }
    }

    private fun createPrompt(context: Context): AutoReadPrompt {
        // A window with "display over other apps", otherwise a heads-up notification.
        if (canDrawOverlays(context)) {
            return OverlayAutoReadPrompt(
                context = context,
                onPauseToggle = { togglePause() },
                onCancel = { cancelCurrent() },
                onDisable = { disable(context) },
            )
        }
        return NotificationAutoReadPrompt(context)
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Whether a countdown window (not just a notification) can currently be shown. */
    fun canShowWindow(context: Context): Boolean = canDrawOverlays(context)

    /** Opens the system screen where "display over other apps" is granted for this app. */
    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun isLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
        return keyguard.isKeyguardLocked
    }
}
