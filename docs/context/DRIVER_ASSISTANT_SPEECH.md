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
- Not done yet: opening a messenger from its notification, Microsoft Edge "Read aloud" engine.
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
  - `ElevenLabsSpeechSource` — ElevenLabs REST `/v1/text-to-speech/{voice_id}`, raw `pcm_24000`. Requires an explicit user's key AND an account voice. Model defaults to `eleven_v3`.
  - `InworldSpeechSource` — official `/tts/v1/voice:stream`, NDJSON frames with Base64 24-kHz raw PCM. Basic (Base64) key is passed unchanged; default `inworld-tts-2`.
- `PaidSpeechAccounts`: reads ElevenLabs `GET /v2/voices` and API character quota, Inworld `GET /voices/v1/voices`. No paid TTS is sent when checking credentials or voices. Payment/balance controls open vendor billing pages; ElevenLabs' API character quota is NOT the monetary PAYG balance. User confirms purchases only on vendor's site.
- User-selectable model and voice in Speech settings; no keys are bundled in app code. Keys are stored using the app's existing settings storage and never logged. Sources independently implemented from public API docs rather than Reader's GPL-3.0 code.
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

## Notification reader (2026-11-14; versionCode 100015)

Notification reading is separate from the clipboard observer. It uses
`driver/notifications/KapijujaNotificationListenerService` registered via
`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`. Android binds it **only after**
the user grants Notification Access in system settings; the settings page exposes
`NotificationAccess.granted/openSettings`. It is **disabled by default**.

Allowed applications only: WhatsApp/WhatsApp Business; Instagram; Gmail, Outlook,
Samsung Email, K-9/Thunderbird and Yahoo Mail; Viber; Telegram/Telegram Beta/Plus;
YouTube. Each family has a separate setting; these switches do not affect Android
notification delivery. This is a strict package allowlist, not a package-name substring
search. New providers must be added deliberately.

`NotificationTextExtractor` reads only Android's supplied `Notification.extras`:
`EXTRA_MESSAGES` (MessagingStyle), `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`,
`EXTRA_TEXT`, `EXTRA_SUB_TEXT`, and a fallback ticker. Expanded content takes
precedence over collapsed duplicate previews. Per-notification in-memory fingerprints
prevent duplicate readings across updates and let newly added messages be read without
repeating all prior messages. At most twenty recent structured messages are considered (cloud synthesis still caps audio input to 8,000 characters per reading; local Android TTS may read up to 60,000).
No reading of grouped summaries, calls, foreground-service status, or active media; no
backfilling every active notification on reconnection. No title/body logging or
persistent message capture. Android sensitive-redaction rules are respected; Android
cannot provide text that the source application did not put into the notification.

`AutoReadController` now accepts notification text as a second origin. Both origins
use the same countdown, shared `SpeechCoordinator`, Pause/Resume, Stop, and privacy
guard (no speech while locked or dictating). The fourth overlay button disables **only
notification reading**. The third disables automatic reading from both sources.
The user continues to receive the original Android notifications, unchanged.

`SpeechTextSanitizer` runs before text reaches any TTS engine. It can skip URL
tokens, long numeric sequences, service-only messages, and symbols. These four
preferences apply to clipboard and notification speech. Credentials and verification
codes are suppressed regardless of user filters. When a cloud provider is chosen,
the cleaned notification text is sent to that provider *only after the countdown*.
Local Android TTS does not send text via the app's own cloud API.

The Speech screen is a top-level settings item, not nested under Clipboard.
Its route remains `settings/speech` for backward compatibility. Overlays use
`SpeechLocaleContext` to match `prefs.other.settingsLanguage` even when launched
from the app-level service context. App resources are supplied in en/ru/de/fr/it,
with English fallback for other locales.

QA on Android 17 Samsung:
1. Install v100015 over 100014 (same package/signature; no uninstall).
2. Open Speech settings, grant Android Notification Access and enable the master
   reader and the desired source. Keep the clipboard switch independent.
3. Verify text from each source with collapsed preview, expanded big text, and
   MessagingStyle group chat; check revised notifications are not reread.
4. Copy the same clipboard text twice; verify both copies read.
5. Test Pause/Continue, Stop, Disable all reading, Disable notification reading.
6. Switch interface language among en/ru/de/fr/it and verify overlay labels.
7. Test links, 10-digit codes, OTPs, symbols, screen locked, live dictation.
8. Check Android TTS after service disconnect and after opening/closing keyboard.
9. Test cloud TTS only with explicit user settings and ensure no pre-countdown
   network request.

## Notification reader recovery (2026-11-14)

`NotificationAccess.connected` reflects the system listener binding instead of confusing an OS permission grant with a connected service. On `onListenerConnected` it is true; on disconnect and service destruction it returns to false. A disconnected listener requests an immediate Android `NotificationListenerService.requestRebind` when **both** the user's auto-reading switch and Android's Notification Access remain enabled. Application startup after preferences initialize, and opening the settings screen, also request binding with a 30-second throttle. The Speech screen shows connection status and a manual "Reconnect" button if disconnected.

These are best-effort, event-driven rebind requests — not permanent background polling or a guarantee against manufacturer battery policies. The user must still grant/re-grant Notification Access in Android settings if the OS revokes it. The app never flips the user's notification reader toggle to OFF in a failure or reconnection path.

QA: Simulate service disconnect/force-stop and process restart; reopen Speech settings; verify listener reconnects when Android still grants access, no duplicate backfill and no surprise enable when the user left reading OFF. Test permission revoke and restore; ensure the UI does not claim connected until Android's callback arrives.

## One-button reading controls and separate modal switches — 2026-11-14

The left Smartbar speaker is the **only quick button**. No extra keyboard space.
- **Short tap when BOTH streams OFF:** enables both clipboard and notification reading, provided Android notification access was previously granted. If permission is absent, clipboard reading activates and the OS permission page opens; notification speech remains OFF until explicitly enabled.
- **Short tap when EITHER stream is ON:** disables BOTH and cancels any in-flight automatic reading.
- **Long press while idle:** toggles notification speech independently, even when clipboard reading is OFF. Android Notification Access must be granted to enable this source.
- **Long press during ongoing countdown or any speech:** retains emergency **Stop**.
- The **amber dot** on the Smartbar speaker means notification speech is ON. A lit speaker means **at least one** source is ON.

The overlay countdown/read-aloud window has four actions in two rows: **Pause/Continue**, **Stop/Cancel**, **Disable clipboard**, and **Disable notifications**. Those last two act on their respective sources **independently**: disabling clipboard leaves notification speech running, and disabling notifications leaves clipboard speech running. If the current reading is from the disabled source, it is canceled; otherwise it is unaffected. The fallback Android status notification exposes its own relevant source's disable action. The master Smartbar button can always switch both sources OFF together.

