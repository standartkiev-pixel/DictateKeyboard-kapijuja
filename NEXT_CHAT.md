# NEXT CHAT — Dictate Kapijuja

This is the compact entry point for continued development.

**Read `docs/context/README.md` first.** It routes each task to one small topic document and a short list of source files. Do not load all historical handoffs or the full repository before starting work.

## Current baseline

- Repository: `standartkiev-pixel/DictateKeyboard-kapijuja`
- Main baseline when this file was compacted: `f5e62e7b89170b946a339d97885ce94cf18ef4e4`
- Release line: `0.1.0 RC6`
- Android release package: `net.kapijuja.dictate`
- `DictateController.kt` is growth-frozen by `scripts/architecture-audit.sh`.

Always inspect current `main`; the SHA above is a historical anchor, not a permanent HEAD.

## Active topic — automatic processing during speech pauses

The observed behavior is intentional long-form AUTO segmentation, not an unexplained keyboard restart.

Current code facts:

- clean-install `longformMode` default: `AUTO`;
- automatic pause default: `3` seconds;
- user-facing AUTO pause slider: `2–8` seconds;
- Smart Turn semantic segmentation: OFF by default;
- Silero VAD remains the pause detector when Smart Turn is off;
- a cut sends the completed segment for background transcription while recording continues.

Device bugreport from 2026-09-14 showed the debug keyboard loading its Silero VAD model and did not show a matching Kapijuja fatal exception/ANR in the inspected window.

For this topic, read only:

`docs/context/VOICE_LONGFORM.md`

and the source files it names.

## Safe product direction

The current AUTO design is useful because completed speech can be processed during a pause instead of building one very large request. Do not remove it casually.

For a user who pauses while thinking, try the existing 5–6 second setting before changing the algorithm. `OFF` returns to one-shot-at-the-end behavior; `MANUAL` keeps segmentation but requires explicit Next cuts.

The unresolved UX question is cancellation after an already-processed segment has appeared in the editor. Trace the existing preview/editor ownership before implementing rollback or session Undo. Do not combine that behavior change with a structural refactor.

## Code cleanup direction

Read `docs/context/CONTROLLER_SPLIT_PLAN.md`.

Refactor by small behavior-preserving extractions, one responsibility at a time. The first useful boundary is long-form coordination. After each green extraction, lower the controller line/byte ceiling so the monolith cannot grow back.

Do not start with a wholesale controller/state-machine rewrite.

## Reliability work

For stuck Transcribing, Stop, resend, watchdog, history recovery or network faults, read only:

`docs/context/RELIABILITY.md`

The current recovery/watchdog design is already implemented; avoid duplicating it from old notes.

## Historical material

These files remain available for archaeology and old decisions, but are **not startup reading**:

- `DEVELOPER_HANDOFF.md`
- `FULL_PROJECT_HANDOFF_2026-09-09.txt`
- `KAPIJUJA_VOICE_PROJECT_HANDOFF_2026-09-09.txt`
- `DEVELOPER_GUIDE.md`

Earlier versions of this file are preserved in Git history. Keep the current file short and replace stale statements instead of appending an endless diary.
