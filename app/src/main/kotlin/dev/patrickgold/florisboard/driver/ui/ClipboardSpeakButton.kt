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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.driver.speech.SpeechCoordinator
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon

/** Whether [item] gets a speaker: plain text only, never a clip the source app marked sensitive. */
fun ClipboardItem.isSpeakable(): Boolean =
    type == ItemType.TEXT && !isSensitive && !text.isNullOrBlank()

/**
 * The speaker on a clipboard history card. A tap reads the clip aloud with the selected engine,
 * without pasting it; a second tap (or a tap on another card) stops it. The card itself keeps its
 * normal tap-to-paste and long-press menu, because this button consumes its own clicks.
 */
@Composable
fun ClipboardSpeakButton(item: ClipboardItem, modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val enabled by prefs.speech.historySpeakerEnabled.collectAsState()
    if (!enabled || !item.isSpeakable()) return
    val context = LocalContext.current
    val state by SpeechCoordinator.state.collectAsState()
    val key = "clip-${item.id}"
    val active = when (val s = state) {
        is SpeechCoordinator.State.Preparing -> s.key == key
        is SpeechCoordinator.State.Speaking -> s.key == key
        SpeechCoordinator.State.Idle -> false
    }
    val label = stringRes(if (active) R.string.speech__stop else R.string.speech__speak_clip)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Button,
                onClickLabel = label,
                onClick = { SpeechCoordinator.toggle(context, item.text.orEmpty(), key) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        SnyggIcon(
            imageVector = if (active) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = label,
        )
    }
}
