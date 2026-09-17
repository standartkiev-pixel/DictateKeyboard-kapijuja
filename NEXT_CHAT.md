# NEXT CHAT — Dictate Kapijuja

This is the compact entry point for continued development.

**Read `docs/context/README.md` first.** It routes each task to one small topic document and a short list of source files. Do not load all historical handoffs or the full repository before starting work.

## Current baseline

- Repository: `standartkiev-pixel/DictateKeyboard-kapijuja`
- Release line: `0.1.0 RC6`
- Android release package: `net.kapijuja.dictate`
- Debug package: `net.kapijuja.dictate.debug`
- The earlier documentation/context cleanup is already on `main`.
- `DictateController.kt` is growth-frozen by `scripts/architecture-audit.sh`.

Always inspect current `main`; historical SHAs in topic documents are anchors, not permanent HEADs.

## Active work — provider switches + learned suggestions

Read:

`docs/context/AI_REWORDING_AND_SUGGESTIONS.md`

before changing runtime code.

**The requested runtime changes in that document are not implemented yet.** The document records the current code facts, desired behavior, relevant files and regression checks so the next chat can continue without reconstructing the discussion.

### Provider/rewording goal

The provider editor currently lets the user choose Transcription, Real-time and Rewording models, but does not make it obvious how to turn realtime or automatic rewording off beside those fields.

Required behavior:

- add Real-time transcription ON/OFF in the provider editor;
- add Automatic rewording ON/OFF in the provider editor;
- preserve selected model ids while switches are OFF;
- Automatic rewording OFF must stop automatic GPT/post-processing after dictation;
- the manual magic wand, saved prompts and translation/rewording actions must remain available;
- do not misuse the existing master `rewordingEnabled`, because disabling it removes the manual tools as well;
- keep Single-call multimodal separate and make its automatic prompt folding obey the new automatic-rewording setting;
- trace actual realtime session startup before coding so OFF prevents network/session startup rather than merely hiding UI.

The user currently prefers `gpt-4o-mini-transcribe` in everyday use because `gpt-transcribe` felt slower. Do not force a model change as part of this UI task.

### Suggestions / personal vocabulary goal

Do not build a new learning system. The repository already contains:

- `ime/dictionary/LearnedWords.kt` with learned words, learned bigrams, prefix lookup, frequency/recency scoring and promotion state;
- `ime/nlp/latin/WordLearningGate.kt` with typo filtering and a 1/2/3-sighting learning ladder;
- `suggestion__learn_typed_words` / `learnTypedWords` in preferences, currently defaulting to OFF.

The user wants repeated deliberate words and email addresses to become useful candidates, with frequently used matching values rising in rank. They also report inconsistent candidate-strip visibility around cursor moves and the first keypress. Trace existing learning/ranking and Smartbar state transitions first; expose/fix the existing machinery rather than duplicating it.

An explicit non-intrusive “Add to dictionary” path for a challenged/unknown word is desirable, but inspect existing personal-dictionary, candidate long-press and undo-autocorrect affordances before adding another modal.

Privacy/safety invariants must remain: no learning from password/private/incognito fields, and likely typos must not be promoted aggressively.

## Existing long-form work

For voice pauses, AUTO segmentation, Smart Turn and cancellation semantics, read:

`docs/context/VOICE_LONGFORM.md`

The AUTO segmentation behavior is intentional and already implemented. Do not mix the current provider/suggestion task with a long-form rewrite.

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
