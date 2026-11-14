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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.patrickgold.florisboard.R

/**
 * The visible part of an auto-read: a countdown, then "reading", each of which can be paused.
 * Four accessible overlay controls in two rows: pause/resume, stop, stop all reading, stop notification reading.
 */
interface AutoReadPrompt {
    fun showCountdown(secondsLeft: Int, paused: Boolean)
    fun showReading(paused: Boolean)
    fun hide()
}

/**
 * A compact window at the top of the screen, clear of a messenger's reply field and send button at
 * the bottom. Drawn as an application overlay, which needs "display over other apps" and works with
 * the keyboard hidden or shown.
 */
class OverlayAutoReadPrompt(
    private val context: Context,
    private val isNotification: Boolean,
    private val onPauseToggle: () -> Unit,
    private val onCancel: () -> Unit,
    private val onDisable: () -> Unit,
    private val onDisableNotifications: () -> Unit,
) : AutoReadPrompt {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var title: TextView? = null
    private var pauseButton: Button? = null
    private var cancelButton: Button? = null

    override fun showCountdown(secondsLeft: Int, paused: Boolean) {
        ensureShown()
        title?.text = if (paused) {
            context.getString(if (isNotification) R.string.speech__notification_countdown_paused else R.string.speech__autoread_countdown_paused)
        } else {
            context.getString(if (isNotification) R.string.speech__notification_countdown else R.string.speech__autoread_countdown, secondsLeft)
        }
        pauseButton?.text = pauseLabel(paused)
        cancelButton?.text = context.getString(R.string.speech__autoread_cancel)
    }

    override fun showReading(paused: Boolean) {
        ensureShown()
        title?.text = context.getString(
            if (paused) (if (isNotification) R.string.speech__notification_reading_paused else R.string.speech__autoread_reading_paused)
            else (if (isNotification) R.string.speech__notification_reading else R.string.speech__autoread_reading)
        )
        pauseButton?.text = pauseLabel(paused)
        cancelButton?.text = context.getString(R.string.speech__autoread_stop)
    }

    override fun hide() {
        val view = root ?: return
        root = null
        runCatching { windowManager?.removeViewImmediate(view) }
    }

    private fun pauseLabel(paused: Boolean): String =
        context.getString(if (paused) R.string.speech__autoread_resume else R.string.speech__autoread_pause)

    private fun ensureShown() {
        if (root != null) return
        val wm = windowManager ?: return
        val ctx = context
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

        val titleView = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        fun button(label: String, color: Int, textSp: Float, onClick: () -> Unit) = Button(ctx).apply {
            text = label
            isAllCaps = false
            maxLines = 2
            setPadding(dp(6), 0, dp(6), 0)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            minHeight = dp(56)
            minWidth = 0
            minimumWidth = 0
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(color)
            }
            setOnClickListener { onClick() }
        }
        val pause = button(pauseLabel(false), 0xFF1565C0.toInt(), 16f, onPauseToggle)
        val cancel = button(ctx.getString(R.string.speech__autoread_cancel), 0xFF455A64.toInt(), 16f, onCancel)
        val disable = button(ctx.getString(R.string.speech__disable_all_reading), 0xFFC62828.toInt(), 14f, onDisable)
        val disableAlerts = button(ctx.getString(R.string.speech__disable_notification_reading), 0xFF795548.toInt(), 14f, onDisableNotifications)
        fun row(left: Button, right: Button) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(row(pause, cancel))
            addView(row(disable, disableAlerts), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) })
        }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xEE1E1E1E.toInt())
            }
            addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
            addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(56)
            horizontalMargin = 0.04f
        }
        val added = runCatching { wm.addView(panel, params) }.isSuccess
        if (!added) return
        root = panel
        title = titleView
        pauseButton = pause
        cancelButton = cancel
    }
}

/**
 * Fallback without "display over other apps": a heads-up notification with a countdown chronometer
 * and the same three actions. It never shows the clip's text, so nothing leaks onto the lock
 * screen. Without the notification permission it is simply not shown.
 */
class NotificationAutoReadPrompt(private val context: Context, private val isNotification: Boolean) : AutoReadPrompt {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** What is on screen now, so the 200 ms countdown ticks do not repost the same notification. */
    private var shown: String? = null

    override fun showCountdown(secondsLeft: Int, paused: Boolean) {
        if (!changed("countdown:$paused")) return
        if (paused) {
            post(build(context.getString(if (isNotification) R.string.speech__notification_countdown_paused else R.string.speech__autoread_countdown_paused), paused, reading = false).setShowWhen(false).build())
            return
        }
        post(
            build(context.getString(if (isNotification) R.string.speech__notification_title else R.string.speech__autoread_notification_title), paused, reading = false)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + secondsLeft * 1000L)
                .setShowWhen(true)
                .setTimeoutAfter((secondsLeft + 2) * 1000L)
                .build()
        )
    }

    override fun showReading(paused: Boolean) {
        if (!changed("reading:$paused")) return
        val title = context.getString(
            if (isNotification) {
                if (paused) R.string.speech__notification_reading_paused else R.string.speech__notification_reading
            } else {
                if (paused) R.string.speech__autoread_reading_paused else R.string.speech__autoread_reading
            }
        )
        post(build(title, paused, reading = true).setShowWhen(false).build())
    }

    override fun hide() {
        shown = null
        runCatching { manager?.cancel(NOTIFICATION_ID) }
    }

    private fun changed(state: String): Boolean {
        if (shown == state) return false
        shown = state
        return true
    }

    private fun build(title: String, paused: Boolean, reading: Boolean): Notification.Builder {
        ensureChannel()
        val pauseLabel = context.getString(if (paused) R.string.speech__autoread_resume else R.string.speech__autoread_pause)
        val cancelLabel = context.getString(if (reading) R.string.speech__autoread_stop else R.string.speech__autoread_cancel)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_icon_monochrome)
            .setContentTitle(title)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .addAction(Notification.Action.Builder(null, pauseLabel, action(ACTION_PAUSE)).build())
            .addAction(Notification.Action.Builder(null, cancelLabel, action(ACTION_CANCEL)).build())
            .addAction(Notification.Action.Builder(
                null,
                context.getString(if (isNotification) R.string.speech__disable_notification_reading else R.string.speech__disable_all_reading),
                action(if (isNotification) ACTION_DISABLE_NOTIFICATIONS else ACTION_DISABLE)
            ).build())
    }

    private fun action(name: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        name.hashCode(),
        Intent(context, AutoReadActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(notification: Notification) {
        runCatching { manager?.notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel() {
        val m = manager ?: return
        if (m.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.speech__autoread_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        m.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "kapijuja_autoread"
        const val NOTIFICATION_ID = 0xA7D0
        const val ACTION_PAUSE = "dev.patrickgold.florisboard.driver.autoread.PAUSE"
        const val ACTION_CANCEL = "dev.patrickgold.florisboard.driver.autoread.CANCEL"
        const val ACTION_DISABLE = "dev.patrickgold.florisboard.driver.autoread.DISABLE"
        const val ACTION_DISABLE_NOTIFICATIONS = "dev.patrickgold.florisboard.driver.autoread.DISABLE_NOTIFICATIONS"
    }
}

/** Receives the notification's "pause", "cancel" and "turn off" buttons. Not exported. */
class AutoReadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationAutoReadPrompt.ACTION_PAUSE -> AutoReadController.togglePause()
            NotificationAutoReadPrompt.ACTION_CANCEL -> AutoReadController.cancelCurrent()
            NotificationAutoReadPrompt.ACTION_DISABLE -> AutoReadController.disable(context)
            NotificationAutoReadPrompt.ACTION_DISABLE_NOTIFICATIONS -> AutoReadController.disableNotifications(context)
        }
    }
}
