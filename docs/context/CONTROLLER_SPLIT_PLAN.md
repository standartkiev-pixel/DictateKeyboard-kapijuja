# DictateController safe decomposition plan

`DictateController.kt` is currently growth-frozen by CI. The goal is not a large rewrite; it is a sequence of small behavior-preserving extractions with tests between them.

## Rules

- Never mix a structural extraction with a UX/behavior change in the same step.
- Preserve public/controller entry points first; move implementation behind focused collaborators.
- Keep coroutine ownership, request generation checks and terminal cleanup semantics unchanged.
- Run phone + Wear compile/unit CI after every extraction.
- After an extraction is green, lower `max_lines` and `max_bytes` in `scripts/architecture-audit.sh` to the new controller size.
- If a moved block has no focused tests, add characterization tests before moving it.

## Suggested extraction order

### 1. Long-form coordinator

Move segmented-session bookkeeping, VAD/splitter ownership, rotate/flush/drain and rescue assembly into a focused long-form coordinator.

Keep `DictateController` as the owner of UI state/editor integration initially. This is the most relevant extraction for current pause work and has a clear boundary around `LiveSpeechSplitter` and `LongFormSession`.

### 2. Batch transcription lifecycle

Move provider request job ownership, heartbeat/watchdog, retry policy, cancellation generation and terminal error mapping into a batch lifecycle component.

Do not alter timeout/retry behavior during this extraction.

### 3. Audio retention/recovery

Move retained-audio, resend/recovery staging and terminal cleanup decisions behind one component with explicit privacy/sensitive-field inputs.

### 4. History replay

Move one-shot provider selection, replay temp ownership and history metadata update logic out of the controller.

### 5. Rewording/prompt execution

Extract prompt/rewording request orchestration after voice lifecycle pieces are stable. Preserve queue order and current selection/editor behavior.

## What not to extract first

Do not begin with a giant state-machine rewrite, editor abstraction replacement, or sweeping coroutine conversion. Those changes touch too many failure/recovery paths at once and make regressions difficult to localize.

## Definition of a successful extraction

The controller is smaller, CI is green, device-visible behavior is unchanged, existing recovery/cancel semantics remain intact, and the architecture ceiling is reduced so the moved responsibility cannot drift back into the monolith.
