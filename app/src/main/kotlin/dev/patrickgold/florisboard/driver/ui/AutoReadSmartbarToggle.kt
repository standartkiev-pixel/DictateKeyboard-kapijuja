/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.driver.autoread.AutoReadController
import dev.patrickgold.florisboard.driver.speech.SpeechCoordinator
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon

/**
 * The auto-read switch, at the far left of the Smartbar so it is never confused with the
 * microphone on the right. On: a filled speaker on an accent circle. Off: a crossed-out speaker.
 * Tap toggles auto-read; long press stops any reading in progress.
 */
@Composable
fun AutoReadSmartbarToggle(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val visible by prefs.speech.autoReadSmartbarToggle.collectAsState()
    if (!visible) return
    val enabled by prefs.speech.autoReadEnabled.collectAsState()
    val accent by prefs.theme.accentColor.collectAsState()
    val context = LocalContext.current
    val label = stringRes(R.string.speech__autoread_toggle)
    val stateLabel = stringRes(if (enabled) R.string.speech__autoread_state_on else R.string.speech__autoread_state_off)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .background(if (enabled) accent.copy(alpha = 0.85f) else Color.Transparent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Switch,
                onLongClick = { SpeechCoordinator.stop() },
                onClick = { AutoReadController.setEnabled(context, !enabled) },
            )
            .semantics {
                contentDescription = label
                stateDescription = stateLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        SnyggIcon(
            imageVector = if (enabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
        )
    }
}
