#!/usr/bin/env python3
"""
Static regression guard for Kapijuja BYOK backup/restore.
Do not output credentials; this audit reads only repository source files.
Actual on-device export/restore still needs integration QA.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
prefs = (ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt").read_text()
backup = (ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt").read_text()
restore = (ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt").read_text()
accounts = (ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/ProviderAccount.kt").read_text()

required_stored_prefs = [
    "dictate__provider_accounts",  # All dictation keys, not just the active OpenAI account.
    "speech__openai_api_key",
    "speech__eleven_api_key",
    "speech__inworld_api_key",
    "speech__azure_key",
    "speech__google_api_key",
    "speech__xai_api_key",
]
for key in required_stored_prefs:
    assert f'key = "{key}"' in prefs, f"Missing persisted preference for {key}"
assert "val apiKey: String" in accounts, "ProviderAccount API key no longer serialized as a field"
assert "json.encodeToString(value)" in accounts, "ProviderAccounts serializer must include API keys"
assert "json.decodeFromString(value)" in accounts, "ProviderAccounts deserializer must restore API keys"
assert "var jetprefDatastore by mutableStateOf(true)" in backup, "Full settings backup must default on"
assert "FlorisPreferenceStore.export(fileBasedStorage).getOrThrow()" in backup, "Full settings export was changed"
assert "FlorisPreferenceStore.import(importStrategy, fileBasedStorage).getOrThrow()" in restore, "Full settings import was changed"
print("API-key backup contract: preference declarations, serializer, full export/import OK")
