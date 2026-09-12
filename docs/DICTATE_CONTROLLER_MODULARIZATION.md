# Dictate controller modularization

`DictateController.kt` currently coordinates recording, transcription, long-form sessions,
recovery, provider requests, UI state, and output. It is working code, but keeping these
responsibilities in one large source file makes reviews and safe automated edits harder.

The refactor must be incremental. Every stage must preserve user-visible behavior, pass the
complete JVM test graph, compile the phone and Wear applications, and pass the release fault suite.
No stage should combine structural movement with a new feature.

## Stages

1. Freeze controller growth and reject corrupted source in CI.
2. Extract request timeout and cancellation orchestration with deterministic tests. (Completed.)
3. Extract the long-form session coordinator while preserving ordered output and rescue audio.
4. Extract stopped-session recovery and retained-audio ownership.
5. Extract recording lifecycle and audio-route observation. (Input switching extracted; lifecycle pending.)
6. Extract provider request construction and response normalization.
7. Leave `DictateController` as the lifecycle-facing coordinator and lower the CI size ceiling.

The size ceiling in `scripts/architecture-audit.sh` must only move downward. Small compatibility
adapters may remain temporarily, but new responsibilities must not be added to the controller.
