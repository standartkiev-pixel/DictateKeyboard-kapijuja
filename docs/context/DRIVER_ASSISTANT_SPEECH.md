# Driver Assistant — reading text aloud

Spec: `docs/roadmap/KAPIJUJA_DRIVER_ASSISTANT_SPEC_2026-11-11.md` (issue #7).

## What exists now

- Speaker button on every **text** card in clipboard history (`driver/ui/ClipboardSpeakButton.kt`,
  one call in `ClipboardInputLayout.ClipItemView`). Tap = read aloud without pasting; tap again = stop.
  Hidden for images/videos, blank clips and clips the source app marked sensitive.
  Pref `speech.historySpeakerEnabled` (default on: it only ever acts on an explicit tap).
- Settings screen *Clipboard ▸ Read aloud* (`driver/ui/SpeechSettingsScreen.kt`, route `Settings.Speech`).
- Auto-read (`driver/autoread/`): `AutoReadController` collects `ClipboardManager.externalClipFlow`
  (clips copied by other apps; the keyboard's own copies are not emitted), filters with
  `AutoReadFilter` (min length, sensitive, links, codes/passwords, own dictation copy via
  `DictateAccessibilityService.lastClipText`), skips lock screen and active dictation, shows
  `AutoReadPrompt` (window at the top: `TYPE_APPLICATION_OVERLAY` when "display over other apps"
  is granted, else accessibility overlay when the Dictate service is on, else a heads-up
  notification) and calls `SpeechCoordinator.speak` only after the countdown. Latest copy wins.
  Turning it on without a way to draw the window opens the overlay permission screen. Settings show
  permission status and the last clip's outcome (`AutoReadController.lastClip`, no text kept). Off by default; toggle `AutoReadSmartbarToggle` is the leftmost Smartbar item.
- Volume: speech uses `USAGE_MEDIA` (media volume) plus a `LoudnessEnhancer` boost (default +6 dB).
- Not done yet: notification assistant, messenger opening, Microsoft Edge "Read aloud" engine.
- Unverified on devices: whether a default IME gets clipboard callbacks while the keyboard is hidden
  on Samsung/One UI; LoudnessEnhancer on the Android TTS engine's session.

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


## Reliability patch — 2026-11-13

Bugreport on Android 17/Samsung (2026-11-13 16:17) showed system TTS
`stop/getVoices/setLanguage/speak failed: not bound to TTS engine` followed by
`KapijujaSpeech: reading failed: engine=ANDROID`. The system did not turn off
auto-read preferences; a stale cached Android `TextToSpeech` binding and/or
missed clipboard callbacks can make an enabled toggle look inert.

- `AndroidSpeechEngine.speak` retries **once only before speech started**.
  It shuts down the stale instance, waits briefly, and binds afresh.
  Cancellation never retries, and mid-utterance failures never repeat audible text.
- Internal writes are marked in `ClipDescription.extras`, not detected by comparing
  the copied text to the internal text. A real new system copy with identical text
  must generate a second `externalClipFlow` event.
- Duplicate callbacks for the *same* system copy are filtered by
  `ClipDescription.timestamp` (Android API 26+), not text comparison.
- When `FlorisImeService.onStartInputView` runs, `AutoReadController` restarts
  its subscription if needed, and `ClipboardManager` reattaches its system
  callback and reconciles the current timestamp. There is **no periodic polling**.
- Exceptions during clipboard processing/observation are logged without disabling
  `speech.autoReadEnabled`. These state changes never silently reset user settings.
- CI distributes `net.kapijuja.dictate.debug` signed by the repository's pinned
  prerelease certificate SHA-256 `22b02aed48a0ca0aeaa96e276e78ddefa000478edd824c8cb89da6575f068884`.
  Upgrade installation needs a newer versionCode; this patch increments it to 100013.

Device verification: copy text once, copy **the same text** again, close and reopen
keyboard, try after killing/restarting the system TTS engine (or force-stopping its
process), and check the Android TTS test voice. Never assume a CI compile confirms
real background clipboard delivery on Samsung One UI.
