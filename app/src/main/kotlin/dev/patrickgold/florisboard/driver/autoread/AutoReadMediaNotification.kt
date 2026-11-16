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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.driver.speech.SpeechCoordinator
import dev.patrickgold.florisboard.driver.ui.SpeechLocaleContext

/**
 * System media card for the current auto-reading. Unlike Notification.setProgress(), a
 * MediaSession + ACTION_SEEK_TO provides a genuinely draggable seekbar on Android 13+.
 *
 * Android TTS and streaming cloud synthesis are not seekable files: a seek cancels the current
 * utterance and starts synthesis again at the closest sentence/word boundary. The timeline
 * uses an estimated text duration, not a misleading claim of measured audio duration.
 */
internal class AutoReadMediaNotification(context: Context, private val isNotification: Boolean) {
    private val context = context.applicationContext
    private val localeContext = SpeechLocaleContext.wrap(context)
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var cachedDuration: Long = -1L

    private fun ensureSession(): MediaSession {
        session?.let { return it }
        val created = MediaSession(context, "KapijujaReadAloud").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    main.post {
                        if (SpeechCoordinator.activeKey()?.startsWith("auto-") == true &&
                            SpeechCoordinator.isPaused
                        ) AutoReadController.togglePause()
                    }
                }

                override fun onPause() {
                    main.post {
                        if (SpeechCoordinator.activeKey()?.startsWith("auto-") == true &&
                            !SpeechCoordinator.isPaused
                        ) AutoReadController.togglePause()
                    }
                }

                override fun onStop() {
                    main.post { AutoReadController.cancelCurrent() }
                }

                override fun onSeekTo(pos: Long) {
                    main.post {
                        if (SpeechCoordinator.activeKey()?.startsWith("auto-") == true) {
                            val duration = SpeechCoordinator.estimatedDurationMs.value.coerceAtLeast(1L)
                            SpeechCoordinator.seekToFraction(pos.toFloat() / duration)
                        }
                    }
                }
            }, main)
            isActive = true
        }
        session = created
        return created
    }

    fun showReading(paused: Boolean) {
        if (SpeechCoordinator.activeKey()?.startsWith("auto-") != true) return
        val media = ensureSession()
        val duration = SpeechCoordinator.estimatedDurationMs.value.coerceAtLeast(1L)
        val position = (SpeechCoordinator.progress.value.coerceIn(0f, 1f) * duration)
            .toLong().coerceIn(0L, duration)

        val title = localeContext.getString(
            if (isNotification) {
                if (paused) R.string.speech__notification_reading_paused else R.string.speech__notification_reading
            } else {
                if (paused) R.string.speech__autoread_reading_paused else R.string.speech__autoread_reading
            },
        )
        if (cachedDuration != duration) {
            cachedDuration = duration
            media.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                    .build(),
            )
        }
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SEEK_TO,
            )
            .setState(
                if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
                position,
                if (paused) 0f else 1f,
            )
            .build()
        media.setPlaybackState(playbackState)
        ensureChannel()
        val pauseLabel = localeContext.getString(
            if (paused) R.string.speech__autoread_resume else R.string.speech__autoread_pause,
        )
        val stopLabel = localeContext.getString(R.string.speech__autoread_stop)
        val disableLabel = localeContext.getString(
            if (isNotification) R.string.speech__disable_notification_reading
            else R.string.speech__disable_clipboard_reading,
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_icon_monochrome)
            .setContentTitle(title)
            .setContentText("≈ " + formatTime(duration))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(!paused)
            .setShowWhen(false)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(media.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .addAction(
                Notification.Action.Builder(
                    if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    pauseLabel,
                    action(AutoReadActionReceiver::class.java, NotificationAutoReadPrompt.ACTION_PAUSE),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    stopLabel,
                    action(AutoReadActionReceiver::class.java, NotificationAutoReadPrompt.ACTION_CANCEL),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    disableLabel,
                    action(
                        AutoReadActionReceiver::class.java,
                        if (isNotification) NotificationAutoReadPrompt.ACTION_DISABLE_NOTIFICATIONS
                        else NotificationAutoReadPrompt.ACTION_DISABLE,
                    ),
                ).build(),
            )
        runCatching { manager?.notify(NotificationAutoReadPrompt.NOTIFICATION_ID, builder.build()) }
    }

    fun hide() {
        cachedDuration = -1L
        runCatching { manager?.cancel(NotificationAutoReadPrompt.NOTIFICATION_ID) }
        runCatching { session?.isActive = false }
        runCatching { session?.release() }
        session = null
    }

    private fun ensureChannel() {
        val manager = manager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kapijuja speech playback", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
        )
    }

    private fun action(clazz: Class<*>, name: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            name.hashCode(),
            Intent(context, clazz).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private companion object {
        const val CHANNEL_ID = "kapijuja_speech_playback"
    }
}
