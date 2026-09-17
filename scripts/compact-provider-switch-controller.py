from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_one(rel, old, new):
    path = root / rel
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

prefs = "app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt"
replace_one(
    prefs,
    '''        val automaticRewordingEnabled = boolean(\n            key = "dictate__automatic_rewording_enabled",\n            default = true,\n        )\n''',
    '''        val automaticRewordingEnabled = boolean(\n            key = "dictate__automatic_rewording_enabled",\n            default = true,\n        )\n        fun autoRewordingOn(): Boolean = rewordingEnabled.get() && automaticRewordingEnabled.get()\n''',
)

controller = "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt"
replace_one(
    controller,
    '''        if (!prefs.dictate.rewordingEnabled.get() ||\n            !prefs.dictate.automaticRewordingEnabled.get() ||\n            transcript.isBlank()\n        ) return transcript\n''',
    '''        if (!prefs.dictate.autoRewordingOn() || transcript.isBlank()) return transcript\n''',
)
replace_one(
    controller,
    '''    private fun rewordingWillFollow(): Boolean =\n        prefs.dictate.rewordingEnabled.get() &&\n            prefs.dictate.automaticRewordingEnabled.get() &&\n            (prefs.dictate.autoFormattingEnabled.get() || _prompts.value.any { it.autoApply })\n''',
    '''    private fun rewordingWillFollow(): Boolean =\n        prefs.dictate.autoRewordingOn() &&\n            (prefs.dictate.autoFormattingEnabled.get() || _prompts.value.any { it.autoApply })\n''',
)
replace_one(
    controller,
    '''        if (prefs.dictate.rewordingEnabled.get() && prefs.dictate.automaticRewordingEnabled.get()) {\n''',
    '''        if (prefs.dictate.autoRewordingOn()) {\n''',
)

print("Controller-size fix applied")
