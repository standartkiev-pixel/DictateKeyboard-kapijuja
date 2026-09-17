from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_one(rel, old, new):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one match, found {count}\n--- pattern ---\n{old}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Keep the existing master rewording switch for the whole feature/manual tools,
#    but add an independent gate for automatic post-processing.
replace_one(
    "app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt",
    '''        val rewordingEnabled = boolean(\n            key = "dictate__rewording_enabled",\n            default = true,\n        )\n''',
    '''        val rewordingEnabled = boolean(\n            key = "dictate__rewording_enabled",\n            default = true,\n        )\n        // Independent automatic post-processing gate. Keep this separate from rewordingEnabled:\n        // turning automatic rewording off must leave the manual magic-wand prompts/translation available.\n        // Default true preserves the behaviour of existing installs and older stored preferences.\n        val automaticRewordingEnabled = boolean(\n            key = "dictate__automatic_rewording_enabled",\n            default = true,\n        )\n''',
)

# 2) Automatic rewording must be gated in all three automatic paths only.
controller = "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt"
replace_one(
    controller,
    '''    private fun rewordingWillFollow(): Boolean =\n        prefs.dictate.rewordingEnabled.get() &&\n            (prefs.dictate.autoFormattingEnabled.get() || _prompts.value.any { it.autoApply })\n''',
    '''    private fun rewordingWillFollow(): Boolean =\n        prefs.dictate.rewordingEnabled.get() &&\n            prefs.dictate.automaticRewordingEnabled.get() &&\n            (prefs.dictate.autoFormattingEnabled.get() || _prompts.value.any { it.autoApply })\n''',
)
replace_one(
    controller,
    '''        if (!prefs.dictate.rewordingEnabled.get() || transcript.isBlank()) return transcript\n''',
    '''        if (!prefs.dictate.rewordingEnabled.get() ||\n            !prefs.dictate.automaticRewordingEnabled.get() ||\n            transcript.isBlank()\n        ) return transcript\n''',
)
replace_one(
    controller,
    '''        if (prefs.dictate.rewordingEnabled.get()) {\n            if (prefs.dictate.autoFormattingEnabled.get()) {\n''',
    '''        if (prefs.dictate.rewordingEnabled.get() && prefs.dictate.automaticRewordingEnabled.get()) {\n            if (prefs.dictate.autoFormattingEnabled.get()) {\n''',
)

# 3) Expose the existing realtime runtime switch and the new automatic-rewording switch
#    in the provider editor. Both switches are staged locally and only saved with OK.
providers = "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictate/DictateProvidersScreen.kt"
replace_one(
    providers,
    ''') {\n    val prefs by FlorisPreferenceStore\n    val isCustom = preset == null\n''',
    ''') {\n    val prefs by FlorisPreferenceStore\n    val scope = rememberCoroutineScope()\n    val realtimePreference by prefs.dictate.realtimeTranscription.collectAsState()\n    val automaticRewordingPreference by prefs.dictate.automaticRewordingEnabled.collectAsState()\n    val isCustom = preset == null\n''',
)
replace_one(
    providers,
    '''    var pickerKind by remember { mutableStateOf<ModelKind?>(null) }\n''',
    '''    var pickerKind by remember { mutableStateOf<ModelKind?>(null) }\n    // These are global runtime modes, but they live here next to the models they control. Keep their\n    // model ids untouched when disabled, and commit the switch state only if the dialog is confirmed.\n    var realtimeEnabled by remember(account.providerId) { mutableStateOf(realtimePreference) }\n    var automaticRewordingEnabled by remember(account.providerId) {\n        mutableStateOf(automaticRewordingPreference)\n    }\n''',
)
replace_one(
    providers,
    '''        onConfirm = {\n            onSave(\n''',
    '''        onConfirm = {\n            scope.launch {\n                prefs.dictate.realtimeTranscription.set(realtimeEnabled)\n                prefs.dictate.automaticRewordingEnabled.set(automaticRewordingEnabled)\n            }\n            onSave(\n''',
)
replace_one(
    providers,
    '''            LocalModelSection(\n                activeModelId = transcriptionModel,\n                activeStreamingModelId = realtimeModel,\n                onActiveModelChange = { transcriptionModel = it },\n                onActiveStreamingModelChange = { realtimeModel = it },\n                onModelChosen = { chosenOnDevice = true },\n            )\n''',
    '''            LocalModelSection(\n                activeModelId = transcriptionModel,\n                activeStreamingModelId = realtimeModel,\n                onActiveModelChange = { transcriptionModel = it },\n                onActiveStreamingModelChange = { realtimeModel = it },\n                onModelChosen = { chosenOnDevice = true },\n            )\n            EditorToggleRow(\n                title = stringRes(R.string.dictate__providers_realtime_enabled_title),\n                summary = stringRes(R.string.dictate__providers_realtime_enabled_summary),\n                checked = realtimeEnabled,\n                onCheckedChange = { realtimeEnabled = it },\n            )\n''',
)
replace_one(
    providers,
    '''                if (preset?.supportsRealtime == true && preset.curatedRealtimeModels.isNotEmpty()) {\n                    EditorField(\n''',
    '''                if (preset?.supportsRealtime == true && preset.curatedRealtimeModels.isNotEmpty()) {\n                    EditorToggleRow(\n                        title = stringRes(R.string.dictate__providers_realtime_enabled_title),\n                        summary = stringRes(R.string.dictate__providers_realtime_enabled_summary),\n                        checked = realtimeEnabled,\n                        onCheckedChange = { realtimeEnabled = it },\n                    )\n                    EditorField(\n''',
)
replace_one(
    providers,
    '''                    if (customRealtime) {\n                        EditorField(\n''',
    '''                    if (customRealtime) {\n                        EditorToggleRow(\n                            title = stringRes(R.string.dictate__providers_realtime_enabled_title),\n                            summary = stringRes(R.string.dictate__providers_realtime_enabled_summary),\n                            checked = realtimeEnabled,\n                            onCheckedChange = { realtimeEnabled = it },\n                        )\n                        EditorField(\n''',
)
replace_one(
    providers,
    '''            // Single-call multimodal (issue #130): kept at the bottom; when on, this one model transcribes\n''',
    '''            if (showChat) {\n                EditorToggleRow(\n                    title = stringRes(R.string.dictate__providers_automatic_rewording_title),\n                    summary = stringRes(R.string.dictate__providers_automatic_rewording_summary),\n                    checked = automaticRewordingEnabled,\n                    onCheckedChange = { automaticRewordingEnabled = it },\n                )\n            }\n            // Single-call multimodal (issue #130): kept at the bottom; when on, this one model transcribes\n''',
)
replace_one(
    providers,
    '''/**\n * A single labeled text field inside the provider editor dialog. When [onBrowse] is set, a trailing\n''',
    '''@Composable\nprivate fun EditorToggleRow(\n    title: String,\n    summary: String,\n    checked: Boolean,\n    onCheckedChange: (Boolean) -> Unit,\n) {\n    Row(\n        modifier = Modifier\n            .fillMaxWidth()\n            .clickable { onCheckedChange(!checked) }\n            .padding(top = 8.dp, bottom = 4.dp),\n        verticalAlignment = Alignment.CenterVertically,\n    ) {\n        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {\n            Text(title, style = MaterialTheme.typography.bodyLarge)\n            Text(\n                text = summary,\n                style = MaterialTheme.typography.bodySmall,\n                color = MaterialTheme.colorScheme.onSurfaceVariant,\n            )\n        }\n        Switch(checked = checked, onCheckedChange = onCheckedChange)\n    }\n}\n\n/**\n * A single labeled text field inside the provider editor dialog. When [onBrowse] is set, a trailing\n''',
)

# 4) New English source strings. Other locales fall back to values/strings.xml until translated.
strings = ROOT / "app/src/main/res/values/strings.xml"
text = strings.read_text(encoding="utf-8")
marker = "</resources>"
if text.count(marker) != 1:
    raise SystemExit("strings.xml: expected one </resources> marker")
if "dictate__providers_automatic_rewording_title" in text:
    raise SystemExit("strings.xml: provider runtime switch strings already present")
addition = '''\n    <string name="dictate__providers_realtime_enabled_title">Real-time transcription</string>\n    <string name="dictate__providers_realtime_enabled_summary">Use the selected real-time model while recording. Turning this off keeps the model selected and uses normal batch transcription.</string>\n    <string name="dictate__providers_automatic_rewording_title">Automatic rewording</string>\n    <string name="dictate__providers_automatic_rewording_summary">Automatically run formatting and auto-apply prompts after dictation. Manual magic-wand prompts and translation stay available.</string>\n'''
strings.write_text(text.replace(marker, addition + marker), encoding="utf-8")

print("Provider runtime switch patch applied successfully")
