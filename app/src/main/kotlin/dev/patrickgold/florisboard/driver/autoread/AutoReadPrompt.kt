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

import android.annotation.SuppressLint
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

/** The visible part of an auto-read: a countdown with "cancel" and "turn off", then "reading". */
interface AutoReadPrompt {
    fun showCountdown(secondsLeft: Int)
    fun showReading()
    fun hide()
}

/**
 * A compact window at the top of the screen, drawn as an accessibility overlay through the optional
 * Dictate accessibility service, so it needs no "draw over other apps" permission and stays clear of
 * a messenger's reply field and send button at the bottom. Only available while that service is on.
 */
class OverlayAutoReadPrompt(
    private val serviceContext: Context,
    private val onCancel: () -> Unit,
    private val onDisable: () -> Unit,
) : AutoReadPrompt {
    private val windowManager = serviceContext.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var title: TextView? = null
    private var cancelButton: Button? = null

    override fun showCountdown(secondsLeft: Int) {
        ensureShown()
        title?.text = serviceContext.getString(R.string.speech__autoread_countdown, secondsLeft)
    }

    override fun showReading() {
        ensureShown()
        title?.text = serviceContext.getString(R.string.speech__autoread_reading)
        cancelButton?.text = serviceContext.getString(R.string.speech__stop)
    }

    override fun hide() {
        val view = root ?: return
        root = null
        runCatching { windowManager?.removeViewImmediate(view) }
    }

    @SuppressLint("SetTextI18n")
    private fun ensureShown() {
        if (root != null) return
        val wm = windowManager ?: return
        val ctx = serviceContext
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

        val titleView = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        fun button(label: String, color: Int, onClick: () -> Unit) = Button(ctx).apply {
            text = label
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            minHeight = dp(56)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(color)
            }
            setOnClickListener { onClick() }
        }
        val cancel = button(ctx.getString(R.string.speech__autoread_cancel), 0xFF455A64.toInt(), onCancel)
        val disable = button(ctx.getString(R.string.speech__autoread_disable), 0xFFC62828.toInt(), onDisable)
        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            addView(disable, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
        cancelButton = cancel
    }
}

/**
 * Fallback when the accessibility service is off: a heads-up notification with a countdown
 * chronometer and the same two actions. It never shows the clip's text, so nothing leaks onto the
 * lock screen. Without the notification permission it is simply not shown.
 */
class NotificationAutoReadPrompt(
    private val context: Context,
    private val totalSeconds: Int,
) : AutoReadPrompt {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var shownCountdown = false

    override fun showCountdown(secondsLeft: Int) {
        if (shownCountdown) return
        shownCountdown = true
        val deadline = System.currentTimeMillis() + secondsLeft * 1000L
        post(
            build(context.getString(R.string.speech__autoread_notification_title))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(deadline)
                .setShowWhen(true)
                .setTimeoutAfter((totalSeconds + 2) * 1000L)
                .build()
        )
    }

    override fun showReading() {
        post(build(context.getString(R.string.speech__autoread_reading)).setShowWhen(false).build())
    }

    override fun hide() {
        runCatching { manager?.cancel(NOTIFICATION_ID) }
    }

    private fun build(title: String): Notification.Builder {
        ensureChannel()
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_icon_monochrome)
            .setContentTitle(title)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.speech__autoread_cancel), action(ACTION_CANCEL)).build())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.speech__autoread_disable), action(ACTION_DISABLE)).build())
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
        const val ACTION_CANCEL = "dev.patrickgold.florisboard.driver.autoread.CANCEL"
        const val ACTION_DISABLE = "dev.patrickgold.florisboard.driver.autoread.DISABLE"
    }
}

/** Receives the notification's "cancel" and "turn off" buttons. Not exported. */
class AutoReadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationAutoReadPrompt.ACTION_CANCEL -> AutoReadController.cancelCurrent()
            NotificationAutoReadPrompt.ACTION_DISABLE -> AutoReadController.disable(context)
        }
    }
}
