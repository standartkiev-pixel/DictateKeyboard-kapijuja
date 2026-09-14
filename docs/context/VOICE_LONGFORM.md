# Voice long-form / pause segmentation

Current behavior and the smallest source map needed for pause-related work.

## What AUTO does

`DictateLongformMode.AUTO` is the clean-install default.

In AUTO, microphone PCM is also fed to `LiveSpeechSplitter`. Silero VAD detects speech/silence. When a qualifying pause reaches the configured duration, the current long-form segment is rotated and transcribed in the background while recording continues.

This is intentional: it avoids holding one huge recording until the user finally stops, reduces end-of-dictation waiting, and limits how much completed speech is still only buffered locally.

The user-visible effect is that a pause may make recognized text appear in the target field while the microphone remains active. A later phrase is a new segment, so punctuation/capitalization can make it look like a fresh sentence.

## Current settings

Preference source: `AppPrefs.kt`.

- `longformMode`: default `AUTO`.
- `longformAutoSplitSeconds`: default `3` seconds.
- `smartTurnEnabled`: default `false`.

UI source: `DictateScreen.kt`, Recording → Long-form dictation.

AUTO exposes a pause slider from 2 to 8 seconds. With Smart Turn off, this is the silence duration that triggers an automatic cut. Smart Turn is opt-in and downloads its on-device model only when enabled.

For a user who naturally pauses while thinking, 5–6 seconds is a reasonable first setting to try before changing code. OFF returns to one-shot-at-the-end behavior. MANUAL keeps segmented long-form available but requires the user to cut with Next.

## Splitter implementation

Primary file: `dictate/audio/LiveSpeechSplitter.kt`.

Important invariants:

- audio analysis runs off the microphone capture thread;
- its event queue is bounded, so slow VAD/Smart Turn inference cannot grow memory without limit;
- dropping analysis frames must never drop the request-owned recording audio;
- after a cut, fresh speech is required before another pause cut;
- continuous speech has a hard three-minute segment safety limit;
- when Smart Turn is disabled/unavailable, pure Silero silence timing still works;
- manual cutting remains available if VAD/model setup fails.

Do not replace this with an unbounded queue or inference on the capture thread.

## Smart Turn semantics

When enabled, Silero first detects a short pause. Smart Turn then classifies whether the thought appears complete. A complete prediction may cut earlier; an incomplete/unknown prediction continues until the user-configured maximum pause fallback is reached.

When disabled (the default), no semantic model decides sentence completion: the configured silence timer is the effective auto-cut rule.

## Device evidence, 2026-09-14

Bugreport `bugreport-e3qxeea-BP4A.251205.006-2026-09-14-10-08-33.zip` showed the debug keyboard process running Silero VAD from:

`/data/user/0/net.kapijuja.dictate.debug/files/vad/silero_vad.onnx`

The report contained normal dictation/watchdog activity and no matching Kapijuja FATAL EXCEPTION or ANR. This supports treating the observed pause behavior as active AUTO segmentation, not a crash/restart symptom.

## UX question still open

AUTO is valuable, but it changes cancellation expectations: the user may decide after a pause that the spoken material should be discarded even though a segment has already been processed/displayed.

Before changing cancellation, trace the exact ownership of segmented preview text through:

- `flushSegment(...)`;
- background segment transcription;
- `realtimeShown` / composing preview handling;
- finalization;
- explicit cancel/discard.

Desired future UX should be chosen explicitly between these models:

1. committed chunks are durable immediately (maximum recovery, cancellation affects only current unflushed speech);
2. chunks are visually previewed/composing until final Stop, so whole-session Cancel can roll them back;
3. hybrid: durable processing plus an Undo session action that removes text inserted by this recording only.

Do not implement model 2 or 3 without editor-state tests: host apps may alter text/cursor state while dictation is running.

## Safe tuning before refactoring

Do not change VAD thresholds merely because a user pauses often. First tune the existing pause-duration setting.

If product testing shows 8 seconds is still too short, expanding the slider range is a low-risk UI/preference change because the splitter already accepts a millisecond threshold and continuous speech has an independent hard segment cap. Still run unit/CI tests before shipping.
