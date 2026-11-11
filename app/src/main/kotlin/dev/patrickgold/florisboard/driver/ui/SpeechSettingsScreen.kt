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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.settings.dictate.TextInputPreference
import dev.patrickgold.florisboard.driver.speech.GeminiSpeechSource
import dev.patrickgold.florisboard.driver.speech.OpenAiRealtimeSpeechSource
import dev.patrickgold.florisboard.driver.speech.SpeechCoordinator
import dev.patrickgold.florisboard.driver.speech.SpeechEngineId
import dev.patrickgold.florisboard.driver.speech.SpeechVoice
import dev.patrickgold.florisboard.driver.speech.SpeechVoiceCatalog
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.florisDialogScroll
import org.florisboard.lib.compose.stringRes

private const val SAMPLE_KEY = "settings-sample"

/**
 * Settings for reading text aloud: the engine, its voice and key, speed, and the speaker button in
 * clipboard history. Only the selected engine's section is shown, so the screen stays short.
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun SpeechSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__speech__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val engine by prefs.speech.engine.collectAsState()
        val speechState by SpeechCoordinator.state.collectAsState()
        val notSet = stringRes(R.string.dictate__value_not_set)
        val masked: (String) -> String = { key -> if (key.isBlank()) notSet else "••••" + key.takeLast(4) }
        DisposableEffect(Unit) { onDispose { if (SpeechCoordinator.isActive(SAMPLE_KEY)) SpeechCoordinator.stop() } }

        SwitchPreference(
            prefs.speech.historySpeakerEnabled,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = stringRes(R.string.pref__speech__history_speaker__label),
            summary = stringRes(R.string.pref__speech__history_speaker__summary),
        )

        ListPreference(
            prefs.speech.engine,
            icon = Icons.Default.RecordVoiceOver,
            title = stringRes(R.string.pref__speech__engine__label),
            entries = listPrefEntries {
                entry(
                    SpeechEngineId.ANDROID,
                    stringRes(R.string.speech__engine_android),
                    stringRes(R.string.speech__engine_android_summary),
                )
                entry(
                    SpeechEngineId.OPENAI,
                    stringRes(R.string.speech__engine_openai),
                    stringRes(R.string.speech__engine_openai_summary),
                )
                entry(
                    SpeechEngineId.AZURE,
                    stringRes(R.string.speech__engine_azure),
                    stringRes(R.string.speech__engine_azure_summary),
                )
                entry(
                    SpeechEngineId.GOOGLE,
                    stringRes(R.string.speech__engine_google),
                    stringRes(R.string.speech__engine_google_summary),
                )
                entry(
                    SpeechEngineId.XAI,
                    stringRes(R.string.speech__engine_xai),
                    stringRes(R.string.speech__engine_xai_summary),
                )
            },
        )

        val sampleActive = speechState.let {
            (it is SpeechCoordinator.State.Preparing && it.key == SAMPLE_KEY) ||
                (it is SpeechCoordinator.State.Speaking && it.key == SAMPLE_KEY)
        }
        val sampleText = stringRes(R.string.speech__sample_text)
        Preference(
            icon = if (sampleActive) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            title = stringRes(if (sampleActive) R.string.speech__stop else R.string.pref__speech__test__label),
            summary = stringRes(R.string.pref__speech__test__summary),
            onClick = { SpeechCoordinator.toggle(context, sampleText, SAMPLE_KEY) },
        )

        DialogSliderPreference(
            prefs.speech.speedPercent,
            title = stringRes(R.string.pref__speech__speed__label),
            valueLabel = { "$it%" },
            min = 50,
            max = 200,
            stepIncrement = 5,
        )
        TextInputPreference(
            pref = prefs.speech.language,
            title = stringRes(R.string.pref__speech__language__label),
            placeholder = "ru-RU",
        )
        SwitchPreference(
            prefs.speech.fallbackToAndroid,
            title = stringRes(R.string.pref__speech__fallback__label),
            summary = stringRes(R.string.pref__speech__fallback__summary),
        )

        when (engine) {
            SpeechEngineId.ANDROID -> PreferenceGroup(title = stringRes(R.string.speech__engine_android)) {
                AndroidEngineSection()
            }
            SpeechEngineId.OPENAI -> PreferenceGroup(title = stringRes(R.string.speech__engine_openai)) {
                TextInputPreference(
                    pref = prefs.speech.openAiApiKey,
                    title = stringRes(R.string.pref__speech__api_key__label),
                    isSecret = true,
                    summaryProvider = masked,
                )
                VoicePickerPreference(prefs.speech.openAiVoice, SpeechVoiceCatalog.openAi)
                TextInputPreference(
                    pref = prefs.speech.openAiModel,
                    title = stringRes(R.string.pref__speech__model__label),
                    placeholder = OpenAiRealtimeSpeechSource.DEFAULT_MODEL,
                    summaryProvider = { it.ifBlank { OpenAiRealtimeSpeechSource.DEFAULT_MODEL } },
                )
                Note(stringRes(R.string.pref__speech__openai_note))
                Note(stringRes(R.string.pref__speech__cloud_privacy))
            }
            SpeechEngineId.AZURE -> PreferenceGroup(title = stringRes(R.string.speech__engine_azure)) {
                TextInputPreference(
                    pref = prefs.speech.azureKey,
                    title = stringRes(R.string.pref__speech__api_key__label),
                    isSecret = true,
                    summaryProvider = masked,
                )
                TextInputPreference(
                    pref = prefs.speech.azureRegion,
                    title = stringRes(R.string.pref__speech__azure_region__label),
                    placeholder = "westeurope",
                )
                VoicePickerPreference(prefs.speech.azureVoice, SpeechVoiceCatalog.azureSuggestions)
                Note(stringRes(R.string.pref__speech__cloud_privacy))
            }
            SpeechEngineId.GOOGLE -> PreferenceGroup(title = stringRes(R.string.speech__engine_google)) {
                TextInputPreference(
                    pref = prefs.speech.googleApiKey,
                    title = stringRes(R.string.pref__speech__api_key__label),
                    isSecret = true,
                    summaryProvider = masked,
                )
                VoicePickerPreference(prefs.speech.googleVoice, SpeechVoiceCatalog.google)
                TextInputPreference(
                    pref = prefs.speech.googleModel,
                    title = stringRes(R.string.pref__speech__model__label),
                    placeholder = GeminiSpeechSource.DEFAULT_MODEL,
                    summaryProvider = { it.ifBlank { GeminiSpeechSource.DEFAULT_MODEL } },
                )
                Note(stringRes(R.string.pref__speech__cloud_privacy))
            }
            SpeechEngineId.XAI -> PreferenceGroup(title = stringRes(R.string.speech__engine_xai)) {
                TextInputPreference(
                    pref = prefs.speech.xaiApiKey,
                    title = stringRes(R.string.pref__speech__api_key__label),
                    isSecret = true,
                    summaryProvider = masked,
                )
                VoicePickerPreference(prefs.speech.xaiVoice, SpeechVoiceCatalog.xai)
                Note(stringRes(R.string.pref__speech__cloud_privacy))
            }
        }
    }
}

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.AndroidEngineSection() {
    val context = LocalContext.current
    val enginePackage by prefs.speech.androidEngine.collectAsState()
    val loading = stringRes(R.string.speech__voices_loading)
    val engines by produceState<List<SpeechVoice>?>(initialValue = null) {
        value = runCatching { SpeechCoordinator.androidEngine(context).engines() }.getOrDefault(emptyList())
    }
    val voices by produceState<List<SpeechVoice>?>(initialValue = null, enginePackage) {
        value = null
        value = runCatching { SpeechCoordinator.androidEngine(context).voices(enginePackage) }.getOrDefault(emptyList())
    }
    val systemDefault = stringRes(R.string.speech__system_default)
    VoicePickerPreference(
        pref = prefs.speech.androidEngine,
        voices = listOf(SpeechVoice("", systemDefault)) + engines.orEmpty(),
        title = stringRes(R.string.pref__speech__android_engine__label),
        allowCustom = false,
        loadingLabel = if (engines == null) loading else null,
    )
    VoicePickerPreference(
        pref = prefs.speech.androidVoice,
        voices = listOf(SpeechVoice("", stringRes(R.string.speech__voice_by_language))) + voices.orEmpty(),
        allowCustom = false,
        loadingLabel = if (voices == null) loading else null,
    )
    DialogSliderPreference(
        prefs.speech.androidPitchPercent,
        title = stringRes(R.string.pref__speech__android_pitch__label),
        valueLabel = { "$it%" },
        min = 50,
        max = 200,
        stepIncrement = 5,
    )
}

/**
 * A voice (or engine) chooser: a radio list of [voices], plus a free-text id when [allowCustom], so a
 * voice newer than this list can still be used without an app update.
 */
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.VoicePickerPreference(
    pref: PreferenceData<String>,
    voices: List<SpeechVoice>,
    title: String = stringRes(R.string.pref__speech__voice__label),
    allowCustom: Boolean = true,
    loadingLabel: String? = null,
) {
    val value by pref.collectAsState()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val current = voices.firstOrNull { it.id == value }?.label ?: value.ifBlank { voices.firstOrNull()?.label.orEmpty() }
    Preference(
        title = title,
        summary = loadingLabel ?: current,
        onClick = { open = true },
    )
    if (!open) return
    var selected by remember(value) { mutableStateOf(value) }
    var custom by remember(value) { mutableStateOf(if (voices.any { it.id == value }) "" else value) }
    JetPrefAlertDialog(
        scrollModifier = florisDialogScroll(),
        title = title,
        confirmLabel = stringRes(R.string.action__ok),
        dismissLabel = stringRes(R.string.action__cancel),
        onConfirm = {
            val chosen = if (allowCustom && custom.isNotBlank()) custom.trim() else selected
            scope.launch { pref.set(chosen) }
            open = false
        },
        onDismiss = { open = false },
    ) {
        Column {
            if (loadingLabel != null) Text(loadingLabel, modifier = Modifier.padding(vertical = 8.dp))
            voices.forEach { voice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = voice.id
                            custom = ""
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = custom.isBlank() && selected == voice.id,
                        onClick = {
                            selected = voice.id
                            custom = ""
                        },
                    )
                    Text(voice.label, modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (allowCustom) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    value = custom,
                    onValueChange = { custom = it },
                    singleLine = true,
                    label = { Text(stringRes(R.string.speech__voice_custom)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
