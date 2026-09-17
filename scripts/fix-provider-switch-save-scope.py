from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictate/DictateProvidersScreen.kt"
text = path.read_text(encoding="utf-8")


def one(old, new):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match, found {count}: {old[:100]!r}")
    text = text.replace(old, new, 1)

one(
    '''                onSave = { updated, makeActive ->\n                    writeKeyring(accounts.put(updated))\n''',
    '''                onSave = { updated, makeActive, realtimeEnabled, automaticRewordingEnabled ->\n                    writeKeyring(accounts.put(updated))\n                    // The dialog disappears immediately after Save, so persist its global runtime modes\n                    // from this screen-owned scope rather than a dialog scope that would be cancelled.\n                    scope.launch {\n                        prefs.dictate.realtimeTranscription.set(realtimeEnabled)\n                        prefs.dictate.automaticRewordingEnabled.set(automaticRewordingEnabled)\n                    }\n''',
)
one(
    '''    onSave: (account: ProviderAccount, makeActive: Boolean) -> Unit,\n''',
    '''    onSave: (\n        account: ProviderAccount,\n        makeActive: Boolean,\n        realtimeEnabled: Boolean,\n        automaticRewordingEnabled: Boolean,\n    ) -> Unit,\n''',
)
one(
    '''    val prefs by FlorisPreferenceStore\n    val scope = rememberCoroutineScope()\n    val realtimePreference by prefs.dictate.realtimeTranscription.collectAsState()\n''',
    '''    val prefs by FlorisPreferenceStore\n    val realtimePreference by prefs.dictate.realtimeTranscription.collectAsState()\n''',
)
one(
    '''        onConfirm = {\n            scope.launch {\n                prefs.dictate.realtimeTranscription.set(realtimeEnabled)\n                prefs.dictate.automaticRewordingEnabled.set(automaticRewordingEnabled)\n            }\n            onSave(\n''',
    '''        onConfirm = {\n            onSave(\n''',
)
one(
    '''                ),\n                chosenOnDevice,\n            )\n''',
    '''                ),\n                chosenOnDevice,\n                realtimeEnabled,\n                automaticRewordingEnabled,\n            )\n''',
)

path.write_text(text, encoding="utf-8")
print("Provider switch save-scope fix applied")
