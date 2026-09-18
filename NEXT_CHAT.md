# NEXT CHAT — Dictate Kapijuja

This is the compact entry point for continued development.

**Read `docs/context/README.md` first.** It routes each task to one small topic document and a short list of source files. Do not load all historical handoffs or the full repository before starting work.

## Current baseline

- Repository: `standartkiev-pixel/DictateKeyboard-kapijuja`
- Current public test release: `v0.1.0-rc.7`
- RC7 release run: `35270908894` — completed successfully
- RC7 target/main commit: `66d9faaac706450ae27347eef6a33d5a77e5a673`
- Test/prerelease package: `net.kapijuja.dictate.debug`
- Future stable package: `net.kapijuja.dictate`
- `DictateController.kt` is growth-frozen by `scripts/architecture-audit.sh`.
- Provider runtime switches are merged in PR #2.
- Candidate-strip monotonic refresh fix is merged in PR #3 and shipped in RC7.
- RC7 release retry hardening is merged; the final release workflow is green.

Always inspect current `main`; historical SHAs in topic documents are anchors, not permanent HEADs.

## What to do next

Start with **device acceptance testing of RC7**, not a new rewrite.

Check on the real phone:

- short dictation;
- long AUTO dictation with natural pauses;
- Stop / Send again;
- Cancel confirmation and recoverable audio;
- History/replay;
- Bluetooth ↔ phone microphone routing while recording;
- Real-time transcription ON/OFF;
- Automatic rewording ON/OFF;
- manual magic wand/translation while Automatic rewording is OFF;
- cursor movement into existing text before the first keypress;
- suggestion strip after repeated cursor moves;
- update over the previous compatible RC without uninstalling.

If a path fails, capture the exact action sequence and a focused device log/bugreport before changing broad code.

## Provider switches + suggestions

Read:

`docs/context/AI_REWORDING_AND_SUGGESTIONS.md`

before changing provider/rewording or personal-suggestion code.

### Provider/rewording — implemented and shipped

The provider editor has independent runtime switches for:

- **Real-time transcription**;
- **Automatic rewording**.

Turning either switch OFF preserves the selected model id.

Automatic rewording OFF stops automatic formatting/auto-apply prompt calls after STT but keeps the manual magic wand, saved prompts and translation/rewording actions available. `rewordingEnabled` remains the master capability/UI switch; do not reuse it as the automatic-only switch.

Realtime OFF uses the existing controller runtime gate, so batch transcription remains available while no realtime session is started.

`Single-call multimodal` remains separate and obeys the automatic-rewording setting when deciding whether to fold automatic instructions into its request.

The user currently prefers `gpt-4o-mini-transcribe` in everyday use because `gpt-transcribe` felt slower. Do not force a model change as part of unrelated work.

### Suggestions / personal vocabulary — existing system

Do not build a second learning system. The repository already contains:

- `ime/dictionary/LearnedWords.kt` with learned words, learned bigrams, prefix lookup, frequency/recency scoring and promotion;
- `ime/nlp/latin/WordLearningGate.kt` with typo filtering and the learning ladder;
- `suggestion__learn_typed_words` / `learnTypedWords`, still opt-in by default;
- long-press on a normal candidate to add it to the personal dictionary;
- long-press on a learned candidate to forget it;
- `LearnedWordsScreen` with **Add now** for manual promotion.

The next product question is whether automatic learning should remain opt-in or become the clean-install default. Before changing the default, test existing learning/ranking on-device, especially email/address-like values and frequent-prefix ranking.

### Candidate-strip consistency — fixed and shipped

PR #3 replaced millisecond timestamp request ids with a monotonic generation sequence shared by async suggestions, direct/glide publications and clears. Publication remains serialized through the existing guard/mutex.

RC7 contains this fix.

If the physical-device symptom survives, inspect composing-region ownership and intentional Smartbar overlays rather than forcing candidates over higher-priority dictation/error/resend/confirmation UI.

## Existing long-form work

For voice pauses, AUTO segmentation, Smart Turn and cancellation semantics, read:

`docs/context/VOICE_LONGFORM.md`

AUTO segmentation is intentional and already implemented.

One UX question remains open: whether whole-session Cancel should remove already processed AUTO chunks, keep them, or expose a session Undo. Do not change this without editor-state tests.

## Reliability work

For stuck Transcribing, Stop, resend, watchdog, History recovery or network faults, read:

`docs/context/RELIABILITY.md`

The current recovery/watchdog design is implemented. Avoid duplicating it from old notes.

## Code cleanup direction

For controller extraction/refactoring, read:

`docs/context/CONTROLLER_SPLIT_PLAN.md`

Keep behavior changes and structural refactors separate. Do not start with a wholesale controller/state-machine rewrite.

## Current dated handoff

For a fuller plain-text summary of what has been done and what remains:

`KAPIJUJA_PROJECT_HANDOFF_2026-09-18.txt`

## Historical material

These files remain available for archaeology and old decisions, but are **not startup reading**:

- `DEVELOPER_HANDOFF.md`
- `FULL_PROJECT_HANDOFF_2026-09-09.txt`
- `KAPIJUJA_VOICE_PROJECT_HANDOFF_2026-09-09.txt`
- `DEVELOPER_GUIDE.md`

Earlier versions of this file are preserved in Git history. Keep the current file short and replace stale statements instead of appending an endless diary.
