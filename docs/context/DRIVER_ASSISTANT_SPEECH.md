# Driver Assistant — reading text aloud

Spec: `docs/roadmap/KAPIJUJA_DRIVER_ASSISTANT_SPEC_2026-11-11.md` (issue #7).

## What exists now

- Speaker button on every **text** card in clipboard history (`driver/ui/ClipboardSpeakButton.kt`,
  one call in `ClipboardInputLayout.ClipItemView`). Tap = read aloud without pasting; tap again = stop.
  Hidden for images/videos, blank clips and clips the source app marked sensitive.
  Pref `speech.historySpeakerEnabled` (default on: it only ever acts on an explicit tap).
- Settings screen *Clipboard ▸ Read aloud* (`driver/ui/SpeechSettingsScreen.kt`, route `Settings.Speech`).
- Not done yet (later stages): auto-read with countdown, notification assistant, messenger opening,
  Microsoft Edge "Read aloud" engine (unofficial endpoint, deliberately left out of the first stage).

## Architecture (`driver/speech/`)

- `SpeechCoordinator` — the only player. One reading at a time; `speak`/`stop`/`toggle` keyed by a
  string (`clip-<id>`). Generation counter so a cancelled reading never touches state. Stops when
  `DictateController` enters `Recording`. Transient audio focus with ducking; focus loss stops.
  Fallback to Android TTS only if the cloud engine is unconfigured or fails **before** any audio.
- `AndroidSpeechEngine` — `TextToSpeech`, optional engine package (RHVoice etc.) and voice name.
- `CloudSpeechSource` implementations, each returning 24 kHz 16-bit mono PCM as it streams:
  - `OpenAiRealtimeSpeechSource` — Realtime WebSocket (`gpt-realtime-2.1-mini` default, setting).
    `/v1/audio/speech` models (`tts-1`, `tts-1-hd`, `gpt-4o-mini-tts`) are retired 2027-01-06.
  - `AzureSpeechSource` — REST, `raw-24khz-16bit-mono-pcm`, streamed body.
  - `GeminiSpeechSource` — `generateContent` AUDIO, one JSON per piece.
  - `XaiSpeechSource` — `POST /v1/tts`, `codec: pcm`.
- `PcmStreamPlayer` — one `AudioTrack` (MODE_STREAM) for the whole text, ~350 ms prebuffer.
- `SpeechTextChunker` — first piece ≈220 chars (fast first sound), then ≈900; sentence-aware.

### Why cloud playback does not stutter

Kapijuja Reader plays each synthesized piece as a separate file in a new `MediaPlayer`, so every
boundary costs a prepare/start gap. Here all pieces go back to back into one `AudioTrack`; the next
piece is requested while the current one plays (at most **one** piece ahead, so a stop wastes at
most one paid request). Audio is played as it arrives instead of after the whole piece downloaded.

## Rules

- Never copy Reader code (GPL-3.0) into this Apache-2.0 app; provider code here is written from the
  providers' public API docs.
- Never log spoken text or keys. `SpeechSettings.toString()` prints only the engine and speed.
- Keys live in the normal preference store (like the existing Dictate provider keys) and are part of
  settings backups.
- Cloud text limit per reading: `SpeechCoordinator.MAX_CLOUD_CHARS`.

## Verification status

Unit tests: `app/src/test/.../driver/speech/SpeechEngineTest.kt` (chunking, WAV/PCM alignment,
request building). Real audio, voices and each provider with a real key need a device check.
