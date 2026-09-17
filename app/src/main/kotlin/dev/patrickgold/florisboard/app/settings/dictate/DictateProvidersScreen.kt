/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.dictateProxyConfig
import dev.patrickgold.florisboard.dictate.provider.LocalModelCatalog
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.TranscriptionApi
import dev.patrickgold.florisboard.dictate.provider.singleCallApplies
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.florisDialogScroll
import org.florisboard.lib.compose.persistentVerticalScrollbar
import org.florisboard.lib.compose.stringRes

/**
 * What the setup wizard asked this screen to open as it appears (issue #273).
 *
 * Two of the four ways out of the provider step already have a screen here — a server of the user's own
 * and the full on-device model list — and rebuilding either inside the wizard would mean a second editor
 * to keep working. A flag rather than a route argument, for the same reason the setup handoff
 * is one: the route is also a deep link, and a deep link carrying an onboarding flag would be a way to
 * reach a half-state from outside the app.
 */
object ProviderSetupHandoff {
    /** Provider id whose editor to open, [ADD_CUSTOM] for a fresh endpoint, or null for nothing. */
    var openEditorFor: String? = null

    /** Sentinel for [openEditorFor]: mint a new custom endpoint instead of editing an existing provider. */
    const val ADD_CUSTOM = "+add-custom"
}

/**
 * The central "AI providers" manager: configure an API key and model(s) for any number of providers
 * (the built-in [ProviderRegistry] presets plus user-defined custom endpoints) and choose which one is
 * active for transcription and which for rewording. Each provider keeps its own credentials in the
 * keyring ([ProviderAccounts]), so switching the active provider never loses another's key.
 */
@Composable
fun DictateProvidersScreen() = FlorisScreen {
    title = stringRes(R.string.dictate__providers_title)
    previewFieldVisible = true
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    content {
        val navController = LocalNavController.current
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        val activeTranscriptionId by prefs.dictate.transcriptionProviderId.collectAsState()
        val scope = rememberCoroutineScope()

        var editingId by remember { mutableStateOf<String?>(null) }
        var activateOnSave by remember { mutableStateOf(false) }
        var localFromSetup by remember { mutableStateOf(false) }

        fun writeKeyring(updated: ProviderAccounts) {
            scope.launch { prefs.dictate.providerAccounts.set(updated) }
        }

        LaunchedEffect(Unit) {
            when (val target = ProviderSetupHandoff.openEditorFor) {
                null -> Unit
                ProviderSetupHandoff.ADD_CUSTOM -> {
                    activateOnSave = true
                    editingId = ProviderAccount.newCustomId()
                }
                else -> {
                    editingId = target
                    localFromSetup = target == ProviderRegistry.LOCAL.id
                }
            }
            ProviderSetupHandoff.openEditorFor = null
        }

        val customAccounts = accounts.accounts.values
            .filter { it.isCustom }
            .sortedBy { it.displayName.lowercase() }

        PreferenceGroup(title = stringRes(R.string.dictate__providers_active_group)) {
            TranscriptionProviderPreference(
                entries = buildList {
                    ProviderRegistry.presets
                        .filter { it.capabilities.transcription }
                        .sortedByDescending { it.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE }
                        .forEach { add(it.id to it.displayName) }
                    customAccounts.forEach { add(it.providerId to customLabel(it)) }
                },
            )
            RewordingProviderPreference(
                entries = buildList {
                    ProviderRegistry.presets
                        .filter { it.capabilities.chat }
                        .forEach { add(it.id to it.displayName) }
                    customAccounts.forEach { add(it.providerId to customLabel(it)) }
                },
                showInfo = accounts.getOrEmpty(activeTranscriptionId).transcriptionViaChat,
            )
            // These switches deliberately live beside the active rewording provider/model rather than on
            // the separate prompt editor screen. "Rewording available" and "run it automatically" are
            // different decisions: keeping the first on preserves the wand/translation prompts, while the
            // second can stay off so ordinary speech is inserted untouched.
            SwitchPreference(
                prefs.dictate.rewordingEnabled,
                icon = Icons.Default.SmartToy,
                modifier = Modifier.settingsSearchAnchor("dictate__rewording_enabled_title"),
                title = stringRes(R.string.dictate__rewording_enabled_title),
                summary = stringRes(R.string.dictate__rewording_enabled_summary),
            )
            SwitchPreference(
                prefs.dictate.autoFormattingEnabled,
                icon = Icons.Default.AutoFixHigh,
                modifier = Modifier.settingsSearchAnchor("dictate__auto_reword_after_transcription_title"),
                title = stringRes(R.string.dictate__auto_reword_after_transcription_title),
                summary = stringRes(R.string.dictate__auto_reword_after_transcription_summary),
                enabledIf = { prefs.dictate.rewordingEnabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.dictate__providers_manage_group)) {
            val keySet = stringRes(R.string.dictate__providers_status_key_set)
            val noKey = stringRes(R.string.dictate__providers_status_no_key)

            val orderedPresets = ProviderRegistry.presets
                .sortedByDescending { it.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE }
            orderedPresets.forEach { preset ->
                val account = accounts[preset.id]
                Preference(
                    icon = providerIcon(preset.id),
                    title = preset.displayName,
                    summary = providerSummary(preset, account, keySet, noKey),
                    onClick = { editingId = preset.id },
                )
            }

            customAccounts.forEach { account ->
                Preference(
                    icon = Icons.Default.Dns,
                    title = customLabel(account),
                    summary = if (account.hasKey || account.customBaseUrl.isNotBlank()) {
                        account.customBaseUrl.ifBlank { keySet }
                    } else {
                        stringRes(R.string.dictate__providers_status_unconfigured)
                    },
                    onClick = { editingId = account.providerId },
                )
            }

            Preference(
                icon = Icons.Default.Add,
                modifier = Modifier.settingsSearchAnchor("dictate__providers_add_custom"),
                title = stringRes(R.string.dictate__providers_add_custom),
                summary = stringRes(R.string.dictate__providers_add_custom_summary),
                onClick = { editingId = ProviderAccount.newCustomId() },
            )
        }

        PreferenceGroup(title = stringRes(R.string.dictate__providers_network_group)) {
            val proxyEnabled by prefs.dictate.proxyEnabled.collectAsState()
            val proxyHost by prefs.dictate.proxyHost.collectAsState()
            val proxyPort by prefs.dictate.proxyPort.collectAsState()
            val proxyOff = stringRes(R.string.dictate__proxy_summary_off)
            Preference(
                icon = Icons.Default.Lan,
                modifier = Modifier.settingsSearchAnchor("dictate__proxy_title"),
                title = stringRes(R.string.dictate__proxy_title),
                summary = if (proxyEnabled && proxyHost.isNotBlank()) {
                    "$proxyHost:$proxyPort"
                } else {
                    proxyOff
                },
                onClick = { navController.navigate(Routes.Settings.DictateProxy) },
            )
            DialogSliderPreference(
                pref = prefs.dictate.requestTimeout,
                icon = Icons.Default.Timer,
                modifier = Modifier.settingsSearchAnchor("dictate__request_timeout_title"),
                title = stringRes(R.string.dictate__request_timeout_title),
                summary = { stringRes(R.string.dictate__request_timeout_summary, "v" to it) },
                valueLabel = { stringRes(R.string.unit__seconds__symbol, "v" to it) },
                min = 30,
                max = 600,
                stepIncrement = 10,
            )
        }

        editingId?.let { id ->
            val preset = ProviderRegistry.byId(id)
            ProviderEditorDialog(
                preset = preset,
                account = accounts.getOrEmpty(id),
                onDismiss = {
                    editingId = null
                    activateOnSave = false
                    localFromSetup = false
                },
                onSave = { updated, makeActive ->
                    writeKeyring(accounts.put(updated))
                    if (activateOnSave) {
                        scope.launch {
                            prefs.dictate.transcriptionProviderId.set(id)
                            prefs.dictate.rewordingProviderId.set(id)
                        }
                    } else if (makeActive && localFromSetup) {
                        scope.launch { prefs.dictate.transcriptionProviderId.set(id) }
                    }
                    editingId = null
                    activateOnSave = false
                    localFromSetup = false
                },
                onDelete = if (preset == null) {
                    {
                        writeKeyring(accounts.remove(id))
                        editingId = null
                        activateOnSave = false
                        localFromSetup = false
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun RewordingProviderPreference(entries: List<Pair<String, String>>, showInfo: Boolean) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val selectedId by prefs.dictate.rewordingProviderId.collectAsState()
    var open by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }

    Preference(
        icon = Icons.Default.SmartToy,
        modifier = Modifier.settingsSearchAnchor("dictate__providers_active_rewording"),
        title = stringRes(R.string.dictate__providers_active_rewording),
        summary = entries.firstOrNull { it.first == selectedId }?.second ?: selectedId,
        trailing = if (showInfo) {
            {
                IconButton(onClick = { infoOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringRes(R.string.dictate__providers_rewording_single_call_note),
                    )
                }
            }
        } else {
            null
        },
        onClick = { open = true },
    )

    if (open) {
        var sel by remember { mutableStateOf(selectedId) }
        JetPrefAlertDialog(
            title = stringRes(R.string.dictate__providers_active_rewording),
            confirmLabel = stringRes(R.string.action__ok),
            dismissLabel = stringRes(R.string.action__cancel),
            onConfirm = {
                scope.launch { prefs.dictate.rewordingProviderId.set(sel) }
                open = false
            },
            onDismiss = { open = false },
        ) {
            val scrollState = rememberScrollState()
            val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .persistentVerticalScrollbar(scrollState, scrollbarColor)
                    .verticalScroll(scrollState)
                    .padding(end = 6.dp),
            ) {
                entries.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sel = id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sel == id, onClick = { sel = id })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (infoOpen) {
        JetPrefAlertDialog(
            scrollModifier = florisDialogScroll(),
            title = stringRes(R.string.dictate__providers_active_rewording),
            confirmLabel = stringRes(R.string.action__ok),
            onConfirm = { infoOpen = false },
            onDismiss = { infoOpen = false },
        ) {
            Text(stringRes(R.string.dictate__providers_rewording_single_call_note))
        }
    }
}

@Composable
private fun TranscriptionProviderPreference(entries: List<Pair<String, String>>) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val selectedId by prefs.dictate.transcriptionProviderId.collectAsState()
    val fallbackEnabled by prefs.dictate.localFallbackEnabled.collectAsState()
    var open by remember { mutableStateOf(false) }

    Preference(
        icon = Icons.Default.Mic,
        modifier = Modifier.settingsSearchAnchor("dictate__providers_active_transcription"),
        title = stringRes(R.string.dictate__providers_active_transcription),
        summary = entries.firstOrNull { it.first == selectedId }?.second ?: selectedId,
        onClick = { open = true },
    )

    if (open) {
        var sel by remember { mutableStateOf(selectedId) }
        var fb by remember { mutableStateOf(fallbackEnabled) }
        val selectionIsLocal =
            ProviderRegistry.byId(sel)?.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE
        JetPrefAlertDialog(
            title = stringRes(R.string.dictate__providers_active_transcription),
            confirmLabel = stringRes(R.string.action__ok),
            dismissLabel = stringRes(R.string.action__cancel),
            onConfirm = {
                scope.launch {
                    prefs.dictate.transcriptionProviderId.set(sel)
                    prefs.dictate.localFallbackEnabled.set(fb)
                }
                open = false
            },
            onDismiss = { open = false },
        ) {
            val scrollState = rememberScrollState()
            val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            Column {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .persistentVerticalScrollbar(scrollState, scrollbarColor)
                        .verticalScroll(scrollState)
                        .padding(end = 6.dp),
                ) {
                    entries.forEach { (id, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sel = id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = sel == id, onClick = { sel = id })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (!selectionIsLocal) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fb = !fb }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = fb, onCheckedChange = { fb = it })
                        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(stringRes(R.string.dictate__local_fallback_title))
                            Text(
                                text = stringRes(R.string.dictate__local_fallback_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun customLabel(account: ProviderAccount): String =
    account.displayName.ifBlank { "Custom server" }

@Composable
private fun providerSummary(
    preset: ProviderPreset,
    account: ProviderAccount?,
    keySet: String,
    noKey: String,
): String {
    if (preset.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE) {
        val context = LocalContext.current
        fun installedName(id: String?): String? = id?.takeIf { it.isNotBlank() }
            ?.let { LocalModelCatalog.byId(it) }
            ?.takeIf { LocalModelManager.isInstalled(context, it.id) }
            ?.displayName
        val names = listOfNotNull(
            installedName(account?.transcriptionModel),
            installedName(account?.realtimeModel),
        ).distinct()
        return if (names.isEmpty()) {
            stringRes(R.string.dictate__local_model_none_selected)
        } else {
            names.joinToString(" · ")
        }
    }
    val caps = buildList {
        if (preset.capabilities.transcription) {
            val stt = stringRes(R.string.dictate__providers_cap_stt)
            add(if (preset.supportsRealtime) "$stt (+ Realtime)" else stt)
        }
        if (preset.capabilities.chat) add(stringRes(R.string.dictate__providers_cap_chat))
    }.joinToString(", ")
    val keyState = if (account?.hasKey == true) keySet else noKey
    return "$keyState · $caps"
}

@Composable
private fun ProviderEditorDialog(
    preset: ProviderPreset?,
    account: ProviderAccount,
    onDismiss: () -> Unit,
    onSave: (account: ProviderAccount, makeActive: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val prefs by FlorisPreferenceStore
    val isCustom = preset == null
    val allowsBaseUrl = isCustom || preset?.allowsCustomBaseUrl == true
    val showTranscription = preset?.capabilities?.transcription ?: true
    val showChat = preset?.capabilities?.chat ?: true

    var displayName by remember { mutableStateOf(account.displayName) }
    var apiKey by remember { mutableStateOf(account.apiKey) }
    var chosenOnDevice by remember { mutableStateOf(false) }
    var baseUrl by remember {
        mutableStateOf(
            account.customBaseUrl.ifBlank { if (preset?.allowsCustomBaseUrl == true) preset.baseUrl else "" },
        )
    }
    val isLocalProvider = preset?.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE
    val legacyStreamingPick = isLocalProvider && LocalModelCatalog.isStreaming(account.transcriptionModel)
    var transcriptionModel by remember {
        mutableStateOf(
            when {
                legacyStreamingPick -> ""
                isLocalProvider -> account.transcriptionModel.ifBlank { preset?.defaultTranscriptionModel.orEmpty() }
                else -> account.transcriptionModel
            },
        )
    }
    var chatModel by remember { mutableStateOf(account.chatModel) }
    var realtimeModel by remember {
        mutableStateOf(if (legacyStreamingPick) account.transcriptionModel else account.realtimeModel)
    }
    var showRealtimePicker by remember { mutableStateOf(false) }
    var cachedModels by remember { mutableStateOf(account.cachedModels) }
    var cachedAudioModels by remember { mutableStateOf(account.cachedAudioModels) }
    var cachedTranscriptionModels by remember { mutableStateOf(account.cachedTranscriptionModels) }
    var transcriptionViaChat by remember { mutableStateOf(account.transcriptionViaChat) }
    var customRealtime by remember { mutableStateOf(account.customRealtime) }
    var customWarmUp by remember { mutableStateOf(account.customWarmUp) }
    var pickerKind by remember { mutableStateOf<ModelKind?>(null) }

    val effectivePreset = when {
        preset == null -> ProviderRegistry.custom(baseUrl, realtime = customRealtime)
        preset.allowsCustomBaseUrl -> preset.copy(baseUrl = baseUrl.ifBlank { preset.baseUrl })
        else -> preset
    }

    JetPrefAlertDialog(
        title = preset?.displayName ?: stringRes(R.string.dictate__providers_custom_title),
        scrollModifier = florisDialogScroll(),
        confirmLabel = stringRes(R.string.action__ok),
        dismissLabel = stringRes(R.string.action__cancel),
        neutralLabel = if (onDelete != null) stringRes(R.string.action__delete) else null,
        onConfirm = {
            onSave(
                account.copy(
                    displayName = displayName.trim(),
                    apiKey = apiKey.trim(),
                    customBaseUrl = baseUrl.trim(),
                    customRealtime = customRealtime,
                    customWarmUp = customWarmUp,
                    transcriptionModel = transcriptionModel.trim(),
                    chatModel = chatModel.trim(),
                    realtimeModel = realtimeModel.trim(),
                    cachedModels = cachedModels,
                    cachedAudioModels = cachedAudioModels,
                    cachedTranscriptionModels = cachedTranscriptionModels,
                    transcriptionViaChat = transcriptionViaChat,
                    cachedModelsAt = if (cachedModels != account.cachedModels) {
                        System.currentTimeMillis()
                    } else {
                        account.cachedModelsAt
                    },
                ),
                chosenOnDevice,
            )
        },
        onDismiss = onDismiss,
        onNeutral = { onDelete?.invoke() },
    ) {
        if (preset?.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE) {
            LocalModelSection(
                activeModelId = transcriptionModel,
                activeStreamingModelId = realtimeModel,
                onActiveModelChange = { transcriptionModel = it },
                onActiveStreamingModelChange = { realtimeModel = it },
                onModelChosen = { chosenOnDevice = true },
            )
        } else {
        Column {
            if (preset?.id == ProviderRegistry.OLLAMA.id) {
                Text(
                    text = stringRes(R.string.dictate__providers_ollama_no_stt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (isCustom) {
                EditorField(
                    label = stringRes(R.string.dictate__providers_field_name),
                    value = displayName,
                    onValueChange = { displayName = it },
                )
            }
            if (allowsBaseUrl) {
                EditorField(
                    label = stringRes(R.string.dictate__base_url_title),
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    placeholder = stringRes(R.string.dictate__base_url_placeholder),
                    keyboardType = KeyboardType.Uri,
                )
            }
            EditorField(
                label = stringRes(R.string.dictate__api_key_title),
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = stringRes(R.string.dictate__api_key_placeholder),
                isSecret = true,
            )
            ConnectionTestRow(preset = effectivePreset, apiKey = apiKey)
            if (showTranscription) {
                EditorField(
                    label = stringRes(
                        if (transcriptionViaChat) {
                            R.string.dictate__providers_field_transcription_rewording_model
                        } else {
                            R.string.dictate__providers_field_transcription_model
                        },
                    ),
                    value = transcriptionModel,
                    onValueChange = { transcriptionModel = it },
                    placeholder = preset?.defaultTranscriptionModel
                        ?: stringRes(R.string.dictate__model_placeholder),
                    onBrowse = { pickerKind = ModelKind.TRANSCRIPTION },
                )
                if (preset?.supportsRealtime == true && preset.curatedRealtimeModels.isNotEmpty()) {
                    EditorField(
                        label = stringRes(R.string.dictate__providers_field_realtime_model),
                        value = realtimeModel,
                        onValueChange = { realtimeModel = it },
                        placeholder = preset.defaultRealtimeModel
                            ?: stringRes(R.string.dictate__model_placeholder),
                        onBrowse = { showRealtimePicker = true },
                    )
                }
                if (isCustom) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customRealtime = !customRealtime }
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringRes(R.string.dictate__providers_custom_realtime),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringRes(R.string.dictate__providers_custom_realtime_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = customRealtime, onCheckedChange = { customRealtime = it })
                    }
                    if (customRealtime) {
                        EditorField(
                            label = stringRes(R.string.dictate__providers_field_realtime_model),
                            value = realtimeModel,
                            onValueChange = { realtimeModel = it },
                            placeholder = stringRes(R.string.dictate__model_placeholder),
                        )
                    }
                }
            }
            if (showChat && !transcriptionViaChat) {
                EditorField(
                    label = stringRes(R.string.dictate__providers_field_chat_model),
                    value = chatModel,
                    onValueChange = { chatModel = it },
                    placeholder = preset?.defaultChatModel
                        ?: stringRes(R.string.dictate__model_placeholder),
                    onBrowse = { pickerKind = ModelKind.CHAT },
                )
                if (isCustom) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customWarmUp = !customWarmUp }
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringRes(R.string.dictate__providers_custom_warm_up),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringRes(R.string.dictate__providers_custom_warm_up_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = customWarmUp, onCheckedChange = { customWarmUp = it })
                    }
                }
            }
            if (showTranscription && showChat) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringRes(R.string.dictate__providers_single_call_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringRes(R.string.dictate__providers_single_call_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = transcriptionViaChat,
                        onCheckedChange = { transcriptionViaChat = it },
                    )
                }
            }
        }
        }
    }

    pickerKind?.let { kind ->
        ModelPickerDialog(
            kind = kind,
            preset = effectivePreset,
            apiKey = apiKey,
            current = if (kind == ModelKind.TRANSCRIPTION) transcriptionModel else chatModel,
            cachedModels = cachedModels,
            cachedAudioModels = cachedAudioModels,
            cachedTranscriptionModels = cachedTranscriptionModels,
            onModelsFetched = { ids, audioIds, sttIds ->
                cachedModels = ids; cachedAudioModels = audioIds; cachedTranscriptionModels = sttIds
            },
            onPick = { picked ->
                if (kind == ModelKind.TRANSCRIPTION) transcriptionModel = picked else chatModel = picked
            },
            onDismiss = { pickerKind = null },
        )
    }

    if (showRealtimePicker && preset != null) {
        RealtimeModelPickerDialog(
            models = preset.curatedRealtimeModels,
            default = preset.defaultRealtimeModel,
            current = realtimeModel,
            onPick = { realtimeModel = it },
            onDismiss = { showRealtimePicker = false },
        )
    }
}

@Composable
private fun RealtimeModelPickerDialog(
    models: List<String>,
    default: String?,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    JetPrefAlertDialog(
        scrollModifier = florisDialogScroll(),
        title = stringRes(R.string.dictate__providers_field_realtime_model),
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
    ) {
        Column {
            models.forEach { model ->
                val isDefault = model == default
                val pick = { onPick(model); onDismiss() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = pick)
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = current == model || (current.isBlank() && isDefault),
                        onClick = pick,
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(model, style = MaterialTheme.typography.bodyLarge)
                        if (isDefault) {
                            Text(
                                stringRes(R.string.dictate__providers_realtime_model_default),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTestRow(preset: ProviderPreset, apiKey: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val okColor = MaterialTheme.colorScheme.primary
    val errColor = MaterialTheme.colorScheme.error
    val failedFallback = stringRes(R.string.dictate__providers_test_failed)
    val successTemplate = context.getString(R.string.dictate__providers_test_success)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        result?.let { (ok, message) ->
            Text(
                text = message,
                color = if (ok) okColor else errColor,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        } ?: Spacer(Modifier.weight(1f))
        TextButton(
            enabled = !testing,
            onClick = {
                testing = true
                result = null
                scope.launch {
                    result = try {
                        val count = OpenAiCompatibleClient
                            .from(
                                preset, apiKey.trim(),
                                baseUrlOverride = preset.baseUrl,
                                proxy = prefs.dictate.dictateProxyConfig(),
                                trustUserCerts = prefs.dictate.trustUserCertificates.get(),
                            )
                            .listModels()
                            .size
                        true to successTemplate.replace("{count}", count.toString())
                    } catch (e: Exception) {
                        false to (e.message ?: failedFallback)
                    } finally {
                        testing = false
                    }
                }
            },
        ) {
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp))
            }
            Text(stringRes(R.string.dictate__providers_test))
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isSecret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onBrowse: (() -> Unit)? = null,
) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier.padding(top = 8.dp),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        visualTransformation = if (isSecret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isSecret) KeyboardType.Password else keyboardType,
        ),
        trailingIcon = when {
            isSecret -> {
                {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            imageVector = if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                        )
                    }
                }
            }
            onBrowse != null -> {
                {
                    IconButton(onClick = onBrowse) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringRes(R.string.dictate__model_picker_title),
                        )
                    }
                }
            }
            else -> null
        },
    )
}
