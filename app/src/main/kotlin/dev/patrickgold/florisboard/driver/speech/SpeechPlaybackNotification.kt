/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.patrickgold.florisboard.driver.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.driver.autoread.AutoReadController
import dev.patrickgold.florisboard.driver.ui.SpeechLocaleContext

/**
 * One transport card for BOTH auto-read and user-selected clipboard history playback.
 *
 * Android's notification progress bar is passive. MediaSession with ACTION_SEEK_TO creates a
 * system-controlled, finger-draggable seekbar where the Android version supports it.
 * Duration is estimated from text, not the measured length of generated audio. Seeking
 * re-synthesizes the remaining text (which may be billed by a cloud TTS provider).
 *
 * This class never places copied text, usernames, or notification bodies into media metadata.
 */
internal class SpeechPlaybackNotification(context: Context) {
    private val app = context.applicationContext
    private val localized = SpeechLocaleContext.wrap(app)
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var displayedKey: String? = null
    private var lastDuration: Long = -1L
    private var lastTitle: String? = null

    private fun ensureSession(): MediaSession {
        session?.let { return it }
        return MediaSession(app, "KapijujaSpeechPlayback").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = handler.post {
                    if (isCurrent() && SpeechCoordinator.isPaused) SpeechCoordinator.resume(app)
                }.let { Unit }

                override fun onPause() = handler.post {
                    if (isCurrent() && !SpeechCoordinator.isPaused) SpeechCoordinator.pause()
                }.let { Unit }

                override fun onStop() = handler.post {
                    if (isCurrent()) stopCurrent()
                }.let { Unit }

                override fun onSeekTo(pos: Long) = handler.post {
                    if (isCurrent()) {
                        val duration = SpeechCoordinator.estimatedDurationMs.value.coerceAtLeast(1L)
                        SpeechCoordinator.seekToFraction(pos.toFloat() / duration)
                    }
                }.let { Unit }

                override fun onSkipToPrevious() = handler.post {
                    if (isCurrent()) SpeechCoordinator.seekByMillis(-60_000L)
                }.let { Unit }

                override fun onSkipToNext() = handler.post {
                    if (isCurrent()) SpeechCoordinator.seekByMillis(60_000L)
                }.let { Unit }

                override fun onRewind() = handler.post {
                    if (isCurrent()) SpeechCoordinator.seekByMillis(-600_000L)
                }.let { Unit }

                override fun onFastForward() = handler.post {
                    if (isCurrent()) SpeechCoordinator.seekByMillis(600_000L)
                }.let { Unit }
            }, handler)
            isActive = true
        }.also { session = it }
    }

    private fun isCurrent(): Boolean =
        displayedKey != null && SpeechCoordinator.activeKey() == displayedKey

    private fun stopCurrent() {
        if (displayedKey?.startsWith("auto-") == true) AutoReadController.cancelCurrent()
        else SpeechCoordinator.stop()
    }

    fun dispatchAction(action: String?) {
        if (!isCurrent()) return
        when (action) {
            ACTION_BACK_1M -> SpeechCoordinator.seekByMillis(-60_000L)
            ACTION_FORWARD_1M -> SpeechCoordinator.seekByMillis(60_000L)
            ACTION_BACK_10M -> SpeechCoordinator.seekByMillis(-600_000L)
            ACTION_FORWARD_10M -> SpeechCoordinator.seekByMillis(600_000L)
            ACTION_TOGGLE_PAUSE -> SpeechCoordinator.togglePause(app)
            ACTION_STOP -> stopCurrent()
        }
    }

    fun show(key: String, paused: Boolean) {
        if (SpeechCoordinator.activeKey() != key || !shouldDisplay(key)) return
        displayedKey = key
        val media = ensureSession()
        val duration = SpeechCoordinator.estimatedDurationMs.value.coerceAtLeast(1L)
        val position = (SpeechCoordinator.progress.value.coerceIn(0f, 1f) * duration)
            .toLong().coerceIn(0L, duration)

        val title = if (key.startsWith("clip-")) {
            localized.getString(R.string.speech__speak_clip)
        } else {
            localized.getString(R.string.speech__autoread_reading)
        }
        if (duration != lastDuration || lastTitle != title) {
            lastDuration = duration
            lastTitle = title
            media.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                    .build(),
            )
        }

        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_REWIND or
                    PlaybackState.ACTION_FAST_FORWARD,
            )
            .setState(
                if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
                position,
                if (paused) 0f else 1f,
            )
            .build()
        media.setPlaybackState(state)

        ensureChannel()
        val pauseLabel = localized.getString(
            if (paused) R.string.speech__autoread_resume else R.string.speech__autoread_pause,
        )
        val stopLabel = localized.getString(R.string.speech__autoread_stop)
        val b = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_icon_monochrome)
            .setContentTitle(title)
            .setContentText("≈ " + formatTime(position) + " / " + formatTime(duration))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(!paused)
            .setShowWhen(false)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(media.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_rew,
                    "−1 min",
                    action(ACTION_BACK_1M),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    pauseLabel,
                    action(ACTION_TOGGLE_PAUSE),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    stopLabel,
                    action(ACTION_STOP),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_ff,
                    "+1 min",
                    action(ACTION_FORWARD_1M),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_ff,
                    "+10 min",
                    action(ACTION_FORWARD_10M),
                ).build(),
            )
        runCatching { manager?.notify(NOTIFICATION_ID, b.build()) }
    }

    fun hide() {
        displayedKey = null
        lastDuration = -1L
        lastTitle = null
        runCatching { manager?.cancel(NOTIFICATION_ID) }
        runCatching { session?.isActive = false }
        runCatching { session?.release() }
        session = null
    }

    private fun action(name: String) = PendingIntent.getBroadcast(
        app,
        name.hashCode(),
        Intent(app, SpeechPlaybackActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel() {
        val m = manager ?: return
        if (m.getNotificationChannel(CHANNEL_ID) != null) return
        m.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kapijuja speech playback", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
        )
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000L
        val hours = seconds / 3600L
        val minutes = seconds / 60L % 60
        return if (hours == 0L) "%d:%02d".format(seconds / 60, seconds % 60)
        else "%d:%02d:%02d".format(hours, minutes, seconds % 60)
    }

    companion object {
        const val NOTIFICATION_ID = 0xA7D1
        const val CHANNEL_ID = "kapijuja_speech_playback"
        private const val ACTION_PREFIX = "net.kapijuja.dictate.speech."
        const val ACTION_BACK_1M = ACTION_PREFIX + "BACK_1M"
        const val ACTION_FORWARD_1M = ACTION_PREFIX + "FORWARD_1M"
        const val ACTION_BACK_10M = ACTION_PREFIX + "BACK_10M"
        const val ACTION_FORWARD_10M = ACTION_PREFIX + "FORWARD_10M"
        const val ACTION_TOGGLE_PAUSE = ACTION_PREFIX + "PAUSE"
        const val ACTION_STOP = ACTION_PREFIX + "STOP"

        fun shouldDisplay(key: String): Boolean =
            key.startsWith("clip-") || key.startsWith("auto-")
    }
}

/** Explicit internal receiver; no third-party application can send playback commands. */
class SpeechPlaybackActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SpeechCoordinator.onPlaybackNotificationAction(intent.action)
    }
}
