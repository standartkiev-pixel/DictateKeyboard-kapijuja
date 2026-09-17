# Dictate Kapijuja — task-scoped context router

Read this file first. Do **not** load every handoff, README, source tree or historical note before working.
The repository history is the archive; current work should use the smallest context needed for the task.

## Current baseline

- Repository: `standartkiev-pixel/DictateKeyboard-kapijuja`
- Main baseline when this router was introduced: `f5e62e7b89170b946a339d97885ce94cf18ef4e4`
- Release line: Dictate Kapijuja `0.1.0 RC6`
- Android release package: `net.kapijuja.dictate`
- Debug package seen in device bugreports: `net.kapijuja.dictate.debug`
- `DictateController.kt` is intentionally growth-frozen by `scripts/architecture-audit.sh`.

Always inspect current `main` before relying on the baseline SHA above.

## Context rule for humans and AI agents

Start with this router plus **one** topic file below. Then inspect only the source files named by that topic.
Expand scope only when an actual dependency requires it.

Do not begin a task by reading `DEVELOPER_HANDOFF.md`, `FULL_PROJECT_HANDOFF_2026-09-09.txt`,
`KAPIJUJA_VOICE_PROJECT_HANDOFF_2026-09-09.txt` and old `NEXT_CHAT.md` history together.
Those are historical/reference material, not mandatory startup context.

Recommended initial budget for a focused task:

- this router;
- one topic file;
- 2–5 directly relevant source files;
- the failing test/log excerpt, if any.

## Route by task

### AI provider switches, automatic rewording, learned words, candidate strip

Read `AI_REWORDING_AND_SUGGESTIONS.md`.
This is the active handoff for:

- Real-time ON/OFF beside the realtime model;
- Automatic rewording ON/OFF without removing the manual magic wand/translation prompts;
- existing learned-word/email/bigram behavior and its currently-off-by-default preference;
- suggestion/candidate strip visibility around typing, cursor moves and competing Smartbar surfaces.

Do not build a second learning store: the topic document names the existing `LearnedWords` / `WordLearningGate` implementation and the paths that must be traced first.

### Voice pauses, automatic chunking, long dictation

Read `VOICE_LONGFORM.md`.
Primary source files:

- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/audio/LiveSpeechSplitter.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt` — only long-form sections
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictate/DictateScreen.kt` — recording/long-form UI only
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt` — `longform*` / `smartTurn*` preferences only

### Stuck Transcribing, cancellation, resend, recovery, network faults

Read `RELIABILITY.md`.
Then inspect only the controller lifecycle section and the selected provider/client involved in the failure.

### Refactoring / controller size / code cleanup

Read `CONTROLLER_SPLIT_PLAN.md` and `scripts/architecture-audit.sh`.
Refactors must be behavior-preserving and should lower the controller ceiling after a successful extraction.

### Release/build/signing

Use `.github/workflows/`, Gradle files and current GitHub Releases/Actions state.
Historical signing notes may be consulted only when signing is actually being performed.
Never generate a replacement permanent signing key merely because an old chat cannot see the existing one.

### UI / Smartbar / prompt panel / History

Start from the concrete screen/composable involved. Do not load provider internals unless the UI bug crosses that boundary.
For the current learned-candidate/Smartbar visibility task, use `AI_REWORDING_AND_SUGGESTIONS.md` instead of this generic route.

## Documentation policy

`NEXT_CHAT.md` is a small entry point, not an append-only diary.
Topic documents describe **current invariants and open work**, not every event that happened.
Completed implementation history belongs in Git commits, release notes or dated archive material.

When a topic file starts accumulating unrelated subjects, split it rather than appending another large section.
When a statement becomes stale, replace it; do not keep both old and new versions in the current-context files.

## Historical/reference documents

These remain useful for archaeology, attribution and recovery of old decisions, but are not startup reading:

- `DEVELOPER_HANDOFF.md`
- `FULL_PROJECT_HANDOFF_2026-09-09.txt`
- `KAPIJUJA_VOICE_PROJECT_HANDOFF_2026-09-09.txt`
- `DEVELOPER_GUIDE.md`

Git history preserves earlier versions of `NEXT_CHAT.md`, so current `NEXT_CHAT.md` may stay compact without losing history.
