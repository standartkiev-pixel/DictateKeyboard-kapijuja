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

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.settings.dictate.TextInputPreference
import dev.patrickgold.florisboard.driver.autoread.AutoReadController
import dev.patrickgold.florisboard.driver.notifications.NotificationAccess
import dev.patrickgold.florisboard.driver.speech.GeminiSpeechSource
import dev.patrickgold.florisboard.driver.speech.OpenAiRealtimeSpeechSource
import dev.patrickgold.florisboard.driver.speech.OpenAiSpeechModels
import dev.patrickgold.florisboard.driver.speech.SpeechCoordinator
import dev.patrickgold.florisboard.driver.speech.SpeechEngineId
import dev.patrickgold.florisboard.driver.speech.SpeechSettings
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
import java.util.Date
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

        PreferenceGroup(title = stringRes(R.string.pref__speech__group_autoread)) {
            SwitchPreference(
                prefs.speech.autoReadEnabled,
                title = stringRes(R.string.pref__speech__autoread__label),
                summary = stringRes(R.string.pref__speech__autoread__summary),
            )
            DialogSliderPreference(
                prefs.speech.autoReadDelaySec,
                title = stringRes(R.string.pref__speech__autoread_delay__label),
                valueLabel = { stringRes(R.string.pref__speech__autoread_delay__value, "v" to it) },
                min = 1,
                max = 10,
                stepIncrement = 1,
            )
            DialogSliderPreference(
                prefs.speech.autoReadMinChars,
                title = stringRes(R.string.pref__speech__autoread_min_chars__label),
                valueLabel = { stringRes(R.string.pref__speech__autoread_min_chars__value, "v" to it) },
                min = 1,
                max = 100,
                stepIncrement = 1,
            )
            SwitchPreference(
                prefs.speech.autoReadSmartbarToggle,
                title = stringRes(R.string.pref__speech__autoread_toggle__label),
                summary = stringRes(R.string.pref__speech__autoread_toggle__summary),
            )
            AutoReadStatusRows()
            Note(stringRes(R.string.pref__speech__autoread_note))
        }

        PreferenceGroup(title = stringRes(R.string.pref__speech__group_notifications)) {
            SwitchPreference(
                prefs.speech.notificationReadEnabled,
                title = stringRes(R.string.pref__speech__notification_master__label),
                summary = stringRes(R.string.pref__speech__notification_master__summary),
            )
            NotificationPermissionRow()
            SwitchPreference(
                prefs.speech.notifyWhatsApp,
                title = stringRes(R.string.pref__speech__source_whatsapp),
            )
            SwitchPreference(
                prefs.speech.notifyInstagram,
                title = stringRes(R.string.pref__speech__source_instagram),
            )
            SwitchPreference(
                prefs.speech.notifyEmail,
                title = stringRes(R.string.pref__speech__source_email),
            )
            SwitchPreference(
                prefs.speech.notifyViber,
                title = stringRes(R.string.pref__speech__source_viber),
            )
            SwitchPreference(
                prefs.speech.notifyTelegram,
                title = stringRes(R.string.pref__speech__source_telegram),
            )
            SwitchPreference(
                prefs.speech.notifyYouTube,
                title = stringRes(R.string.pref__speech__source_youtube),
            )
            Note(stringRes(R.string.pref__speech__notification_privacy_note))
        }

        PreferenceGroup(title = stringRes(R.string.pref__speech__group_cleanup)) {
            SwitchPreference(
                prefs.speech.skipSpokenLinks,
                title = stringRes(R.string.pref__speech__skip_links),
            )
            SwitchPreference(
                prefs.speech.skipSpokenLongNumbers,
                title = stringRes(R.string.pref__speech__skip_numbers),
            )
            SwitchPreference(
                prefs.speech.skipSpokenServiceMessages,
                title = stringRes(R.string.pref__speech__skip_service_messages),
            )
            SwitchPreference(
                prefs.speech.simplifySpokenSymbols,
                title = stringRes(R.string.pref__speech__simplify_symbols),
            )
            Note(stringRes(R.string.pref__speech__cleanup_note))
        }

        // The short way to the one cloud voice most people want; the full list is right below.
        val engineScope = rememberCoroutineScope()
        val setChatGpt: (Boolean) -> Unit = { on ->
            engineScope.launch {
                prefs.speech.engine.set(if (on) SpeechEngineId.OPENAI else SpeechEngineId.ANDROID)
            }
        }
        Preference(
            title = stringRes(R.string.pref__speech__chatgpt__label),
            summary = stringRes(R.string.pref__speech__chatgpt__summary),
            onClick = { setChatGpt(engine != SpeechEngineId.OPENAI) },
            trailing = {
                Switch(checked = engine == SpeechEngineId.OPENAI, onCheckedChange = setChatGpt)
            },
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

        val sampleActive = SpeechCoordinator.keyOf(speechState) == SAMPLE_KEY
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
        DialogSliderPreference(
            prefs.speech.volumeBoostDb,
            title = stringRes(R.string.pref__speech__volume_boost__label),
            valueLabel = { if (it == 0) stringRes(R.string.pref__speech__volume_boost_off) else "+$it dB" },
            min = 0,
            max = 15,
            stepIncrement = 1,
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
                OpenAiSection(masked)
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

/**
 * OpenAI: the key (the dictation key when none is entered here), the voice, and the model picked
 * from the list the account itself reports.
 */
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.OpenAiSection(masked: (String) -> String) {
    val ownKey by prefs.speech.openAiApiKey.collectAsState()
    val accounts by prefs.dictate.providerAccounts.collectAsState()
    val dictationKey = remember(accounts) { SpeechSettings.dictationOpenAiKey(prefs) }
    val effectiveKey = ownKey.trim().ifBlank { dictationKey }
    val sharedKeyLabel = stringRes(R.string.pref__speech__openai_key_shared, "key" to masked(dictationKey))
    TextInputPreference(
        pref = prefs.speech.openAiApiKey,
        title = stringRes(R.string.pref__speech__api_key__label),
        isSecret = true,
        summaryProvider = { key -> if (key.isBlank() && dictationKey.isNotBlank()) sharedKeyLabel else masked(key) },
    )
    VoicePickerPreference(prefs.speech.openAiVoice, SpeechVoiceCatalog.openAi)
    val models by produceState<List<SpeechVoice>?>(initialValue = null, effectiveKey) {
        value = null
        value = if (effectiveKey.isBlank()) {
            emptyList()
        } else {
            runCatching { OpenAiSpeechModels.fetch(effectiveKey) }.getOrDefault(emptyList()).map { SpeechVoice(it, it) }
        }
    }
    val defaultModel = stringRes(R.string.pref__speech__model_default, "model" to OpenAiRealtimeSpeechSource.DEFAULT_MODEL)
    VoicePickerPreference(
        pref = prefs.speech.openAiModel,
        voices = listOf(SpeechVoice("", defaultModel)) + models.orEmpty(),
        title = stringRes(R.string.pref__speech__model__label),
        loadingLabel = if (models == null) stringRes(R.string.speech__models_loading) else null,
    )
}

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.AndroidEngineSection() {
    val context = LocalContext.current
    val enginePackage by prefs.speech.androidEngine.collectAsState()
    val language by prefs.speech.language.collectAsState()
    val sampleText = stringRes(R.string.speech__sample_text)
    val loading = stringRes(R.string.speech__voices_loading)
    val engines by produceState<List<SpeechVoice>?>(initialValue = null) {
        value = runCatching { SpeechCoordinator.androidEngine(context).engines() }.getOrDefault(emptyList())
    }
    val voices by produceState<List<SpeechVoice>?>(initialValue = null, enginePackage, language) {
        value = null
        value = runCatching {
            SpeechCoordinator.androidEngine(context).voices(enginePackage, language)
        }.getOrDefault(emptyList())
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
        // Android does not say what a voice sounds like, so every one can be heard before choosing.
        onPreview = { voice ->
            SpeechCoordinator.speak(context, sampleText, SAMPLE_KEY) {
                it.copy(engine = SpeechEngineId.ANDROID, androidEnginePackage = enginePackage, androidVoice = voice.id)
            }
        },
    )
    Note(stringRes(R.string.pref__speech__android_voice_note))
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
    onPreview: ((SpeechVoice) -> Unit)? = null,
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
                    Text(voice.label, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    if (onPreview != null) {
                        IconButton(onClick = { onPreview(voice) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringRes(R.string.speech__voice_listen),
                            )
                        }
                    }
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

/**
 * Why the countdown window may not appear and what happened to the last copied text, so a missing
 * window or a skipped clip can be explained on the device without logs (no text is shown or stored).
 */
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.NotificationPermissionRow() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(NotificationAccess.granted(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = NotificationAccess.granted(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Preference(
        title = stringRes(R.string.pref__speech__notification_permission),
        summary = stringRes(
            if (granted) R.string.pref__speech__notification_permission_granted
            else R.string.pref__speech__notification_permission_missing
        ),
        onClick = { NotificationAccess.openSettings(context) },
    )
}

@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.AutoReadStatusRows() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(AutoReadController.canDrawOverlays(context)) }
    var notificationsOn by remember { mutableStateOf(notificationsEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = AutoReadController.canDrawOverlays(context)
                notificationsOn = notificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Preference(
        title = stringRes(R.string.pref__speech__autoread_overlay__label),
        summary = stringRes(
            if (overlayGranted) R.string.pref__speech__autoread_overlay__granted
            else R.string.pref__speech__autoread_overlay__missing
        ),
        onClick = { AutoReadController.openOverlayPermissionSettings(context) },
    )
    if (!overlayGranted) {
        Preference(
            title = stringRes(R.string.pref__speech__autoread_notifications__label),
            summary = stringRes(
                if (notificationsOn) R.string.pref__speech__autoread_notifications__on
                else R.string.pref__speech__autoread_notifications__off
            ),
            onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
        )
    }
    val last by AutoReadController.lastClip.collectAsState()
    val clip = last
    Preference(
        title = stringRes(R.string.pref__speech__autoread_last__label),
        summary = if (clip == null) {
            stringRes(R.string.pref__speech__autoread_last__none)
        } else {
            stringRes(
                R.string.pref__speech__autoread_last__value,
                "time" to DateFormat.getTimeFormat(context).format(Date(clip.atMillis)),
                "result" to stringRes(outcomeLabel(clip.outcome)),
                "n" to clip.length,
            )
        },
    )
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun outcomeLabel(outcome: AutoReadController.Outcome): Int = when (outcome) {
    AutoReadController.Outcome.READ -> R.string.speech__autoread_outcome_read
    AutoReadController.Outcome.DISABLED -> R.string.speech__autoread_outcome_disabled
    AutoReadController.Outcome.EMPTY -> R.string.speech__autoread_outcome_empty
    AutoReadController.Outcome.SENSITIVE -> R.string.speech__autoread_outcome_sensitive
    AutoReadController.Outcome.TOO_SHORT -> R.string.speech__autoread_outcome_too_short
    AutoReadController.Outcome.OWN_COPY -> R.string.speech__autoread_outcome_own_copy
    AutoReadController.Outcome.LOOKS_SECRET -> R.string.speech__autoread_outcome_secret
    AutoReadController.Outcome.LINK -> R.string.speech__autoread_outcome_link
    AutoReadController.Outcome.LOCKED -> R.string.speech__autoread_outcome_locked
    AutoReadController.Outcome.DICTATING -> R.string.speech__autoread_outcome_dictating
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
