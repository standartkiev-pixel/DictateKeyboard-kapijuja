# NEXT CHAT — Dictate Kapijuja

This is the compact entry point for continued development.

**Read `docs/context/README.md` first.** It routes each task to one small topic document and a short list of source files. Do not load all historical handoffs or the full repository before starting work.

## Current baseline

- Repository: `standartkiev-pixel/DictateKeyboard-kapijuja`
- Release line: `0.1.0 RC6`
- Android release package: `net.kapijuja.dictate`
- Debug package: `net.kapijuja.dictate.debug`
- `DictateController.kt` is growth-frozen by `scripts/architecture-audit.sh`.
- Provider runtime switches were merged in PR #2, merge commit `2844aaa13e54d5263331da551921a23c1312fe37`.
- Candidate-strip request ordering is under validation in PR #3, branch `kapijuja/suggestion-strip-consistency`.

Always inspect current `main`; historical SHAs in topic documents are anchors, not permanent HEADs.

## Provider switches + suggestions

Read:

`docs/context/AI_REWORDING_AND_SUGGESTIONS.md`

before changing provider/rewording or personal-suggestion code.

### Provider/rewording — implemented on main

The provider editor now has independent runtime switches for:

- **Real-time transcription**;
- **Automatic rewording**.

Turning either switch OFF preserves the selected model id.

Automatic rewording OFF stops automatic formatting/auto-apply prompt calls after STT but keeps the manual magic wand, saved prompts and translation/rewording actions available. `rewordingEnabled` remains the master capability/UI switch; do not reuse it as the automatic-only switch.

Realtime OFF uses the existing controller runtime gate, so batch transcription remains available while no realtime session is started.

`Single-call multimodal` remains separate and obeys the automatic-rewording setting when deciding whether to fold automatic instructions into its request.

The user currently prefers `gpt-4o-mini-transcribe` in everyday use because `gpt-transcribe` felt slower. Do not force a model change as part of unrelated work.

### Suggestions / personal vocabulary — existing system

Do not build a second learning system. The repository already contains:

- `ime/dictionary/LearnedWords.kt` with learned words, learned bigrams, prefix lookup, frequency/recency scoring and promotion state;
- `ime/nlp/latin/WordLearningGate.kt` with typo filtering and the learning ladder;
- `suggestion__learn_typed_words` / `learnTypedWords`, deliberately opt-in by default;
- long-press on a normal candidate to add it to the personal dictionary;
- long-press on a learned candidate to forget it;
- `LearnedWordsScreen` with **Add now** for manual promotion.

Privacy invariants remain: no learning from password/private/incognito fields or non-typed origins, and likely slips should not be promoted aggressively.

### Candidate-strip consistency — PR #3

The reported symptom was that moving the cursor before typing could leave the Smartbar without candidates, while the first keypress suddenly made them appear.

The traced cause is request ordering in `NlpManager`: candidate refreshes used `SystemClock.uptimeMillis()` as an id, while the initial candidate state used the same clock. The first refresh or several back-to-back cursor/key events can therefore share a millisecond and fail the strict newer-than guard.

PR #3 replaces timestamp ids with a monotonic `AtomicLong` generation, uses one generation domain for async/direct/clear publications, and serializes publications through the existing mutex. It adds `SuggestionRequestSequenceTest.kt` for strict back-to-back ordering.

This fix intentionally does **not** change ranking, learning thresholds, autocorrect, dictation, or Smartbar overlay priority.

Before treating it as complete, confirm PR #3 CI is green and validate the cursor-before-first-keypress case on a device. If the symptom survives, inspect composing-region ownership and intentional competing Smartbar surfaces rather than forcing candidates over higher-priority UI.

## Existing long-form work

For voice pauses, AUTO segmentation, Smart Turn and cancellation semantics, read:

`docs/context/VOICE_LONGFORM.md`

The AUTO segmentation behavior is intentional and already implemented. Do not mix provider/suggestion work with a long-form rewrite.

## Reliability work

For stuck Transcribing, Stop, resend, watchdog, history recovery or network faults, read:

`docs/context/RELIABILITY.md`

The current recovery/watchdog design is already implemented; avoid duplicating it from old notes.

## Code cleanup direction

For controller extraction/refactoring, read:

`docs/context/CONTROLLER_SPLIT_PLAN.md`

Keep behavior changes and structural refactors separate. Do not start with a wholesale controller/state-machine rewrite.

## Historical material

These files remain available for archaeology and old decisions, but are **not startup reading**:

- `DEVELOPER_HANDOFF.md`
- `FULL_PROJECT_HANDOFF_2026-09-09.txt`
- `KAPIJUJA_VOICE_PROJECT_HANDOFF_2026-09-09.txt`
- `DEVELOPER_GUIDE.md`

Earlier versions of this file are preserved in Git history. Keep the current file short and replace stale statements instead of appending an endless diary.
