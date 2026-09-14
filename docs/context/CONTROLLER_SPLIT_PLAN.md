# DictateController safe decomposition plan

`DictateController.kt` is currently growth-frozen by CI. The goal is not a large rewrite; it is a sequence of small behavior-preserving extractions with tests between them.

## Existing extraction baseline

Do not rediscover or recreate long-form primitives that already exist:

- `LongFormSession.kt` owns the session state machine facade;
- `LongFormSegmentQueue.kt` owns ordered segment reservation/drain;
- `LongFormSessionResources.kt` owns segment WAVs/jobs;
- `LongFormAudioAssembly.kt` owns rescue/history WAV assembly;
- `LiveSpeechSplitter.kt` owns VAD/Smart Turn pause analysis.

Focused unit tests already exist for the long-form session, queue, resources and audio assembly. Reuse these boundaries.

The remaining long-form weight inside `DictateController` is mainly orchestration: recorder rotation, splitter lifecycle, provider launch, preview/editor updates, terminal drain/finalization and rescue handoff.

## Rules

- Never mix a structural extraction with a UX/behavior change in the same step.
- Preserve public/controller entry points first; move implementation behind focused collaborators.
- Keep coroutine ownership, request generation checks and terminal cleanup semantics unchanged.
- Run phone + Wear compile/unit CI after every extraction.
- After an extraction is green, lower `max_lines` and `max_bytes` in `scripts/architecture-audit.sh` to the new controller size.
- If a moved orchestration path has no focused characterization test, add that test before moving it.

## Suggested extraction order

### 1. Long-form coordinator

Add a thin coordinator around the existing long-form helper classes; do **not** rewrite those classes.

First move only lifecycle/orchestration that can be expressed through callbacks to the controller, for example splitter start/reset and segment reservation/rotation bookkeeping. Keep editor preview mutation, global `UiState`, provider selection and terminal recovery ownership in `DictateController` during the first extraction.

Once that mechanical move is CI-green, a later extraction can move more of rotate/flush/drain orchestration behind explicit callbacks. This keeps each diff reviewable and makes regressions attributable.

### 2. Batch transcription lifecycle

Move provider request job ownership, heartbeat/watchdog, retry policy, cancellation generation and terminal error mapping into a batch lifecycle component.

Do not alter timeout/retry behavior during this extraction.

### 3. Audio retention/recovery

Move retained-audio, resend/recovery staging and terminal cleanup decisions behind one component with explicit privacy/sensitive-field inputs.

### 4. History replay

Move one-shot provider selection, replay temp ownership and history metadata update logic out of the controller.

### 5. Rewording/prompt execution

Extract prompt/rewording request orchestration after voice lifecycle pieces are stable. Preserve queue order and current selection/editor behavior.

## Performance review note

`LiveSpeechSplitter.feed()` currently converts every captured PCM frame into a new `ShortArray` before attempting to enqueue it. Because the analysis queue is deliberately bounded, a saturated queue can cause an allocation that is immediately discarded. That is a plausible low-memory/GC optimization target, but it touches capture-thread timing and hard-segment accounting. Keep it for a separate measured/tested change rather than mixing it with the coordinator extraction.

## What not to extract first

Do not begin with a giant state-machine rewrite, editor abstraction replacement, sweeping coroutine conversion, or a new long-form queue/session implementation. Those changes touch too many failure/recovery paths at once and make regressions difficult to localize.

## Definition of a successful extraction

The controller is smaller, CI is green, device-visible behavior is unchanged, existing recovery/cancel semantics remain intact, and the architecture ceiling is reduced so the moved responsibility cannot drift back into the monolith.
