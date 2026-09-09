# Dictate Kapijuja

**Dictate Kapijuja** is an open-source Android voice keyboard derived from
[Dictate Keyboard](https://github.com/DevEmperor/DictateKeyboard), which itself is built on
[FlorisBoard](https://github.com/florisboard/florisboard).

The goal of this fork is to keep the excellent full keyboard and voice/AI architecture while making
the application independent from the upstream Dictate Cloud payment service and giving the fork its
own Android identity, release-signing configuration and release line.

For the current authoritative development handoff, read **[DEVELOPER_HANDOFF.md](DEVELOPER_HANDOFF.md)** first.
Plain-text copies are available as **[FULL_PROJECT_HANDOFF_2026-09-09.txt](FULL_PROJECT_HANDOFF_2026-09-09.txt)** and
**[KAPIJUJA_VOICE_PROJECT_HANDOFF_2026-09-09.txt](KAPIJUJA_VOICE_PROJECT_HANDOFF_2026-09-09.txt)**.
Developers should also read **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** and **[NEXT_CHAT.md](NEXT_CHAT.md)** before changing recovery/network code.

## Current status

The fork is functional and builds successfully in GitHub Actions.

- Product name: **Dictate Kapijuja**
- Full name: **Dictate Kapijuja Keyboard**
- Android application id: `net.kapijuja.dictate`
- Current fork version line: `0.1.0` / versionCode `1`
- Main development branch: `main`
- Bootstrap/history branch retained: `kapijuja-bootstrap`
- The official Dictate app and Dictate Kapijuja can be installed side by side.
- Release-signing configuration and the stable alias `kapijuja_voice_release` are prepared; the external JKS itself is not stored in Git and must be verified/recovered before the first signed release.
- Dictate Cloud and Google Play Billing are intentionally removed.
- BYOK online providers, custom/self-hosted endpoints and on-device STT remain available.
- CI builds a debug APK, uploads it as an artifact and runs the unit test suite.
- Recovery/watchdog source checkpoint `ba315c1b3ed7eb26a8b8f835287e51de91bb7eb1` passed GitHub Actions run `34337994539` completely.

## What was removed from upstream Dictate

Dictate Kapijuja does **not** include the upstream prepaid Dictate Cloud service.

Removed components include:

- the entire `cloud/` server project;
- `DictateCloud`, `DictateCloudApi`, `DictateCloudBilling`, `DictateCloudPack`;
- the Dictate Cloud settings/store screen and navigation routes;
- Cloud wallet, balance, recovery-code and low-credit UI/state;
- Google Play Billing dependency and `com.android.vending.BILLING` permission;
- Cloud-specific tests, icons, strings and purchase UI;
- upstream funding/store links that would direct Kapijuja users to the original product.

CI contains a regression guard which fails if Dictate Cloud/Billing wiring is accidentally reintroduced.

## AI providers

Dictate Kapijuja talks directly to the provider selected by the user. The current architecture supports
providers such as OpenAI, Gemini, Groq, OpenRouter, Mistral, Deepgram, Soniox, ElevenLabs, AssemblyAI
and OpenAI-compatible custom/self-hosted servers.

Provider credentials remain on the device and are sent only to the configured endpoint.

The upstream `ProviderRegistry` architecture is intentionally retained because it cleanly separates
provider configuration from the keyboard/UI.

## On-device transcription

On-device transcription uses:

- sherpa-onnx;
- ONNX Runtime Android;
- downloadable speech models;
- bundled Silero VAD asset for voice activity detection.

The repository intentionally does **not** commit the large sherpa/ONNX native binaries. Fetch them with:

```bash
./tools/fetch-sherpa-onnx.sh
```

The script downloads pinned artifacts and verifies SHA-256 checksums.

Large speech models and several keyboard language assets are also downloaded on demand instead of
being committed to Git.

### Temporary upstream asset dependency

Some static downloadable model/dictionary/language-pack URLs still point to original Dictate GitHub
Release Assets. This is deliberate for now. They are static files, not the removed Dictate Cloud
transcription/payment backend.

Important files include:

- `LocalModelCatalog.kt`
- `BigramCatalog.kt`
- `GlideDictionaryCatalog.kt`
- `PinyinPackManager.kt`

Do not replace these URLs casually. If we later mirror them under Kapijuja releases, preserve file
names, versions and hashes.

## Build

Important toolchain versions are pinned by Gradle. The project currently uses Kotlin/Android Gradle
Plugin/Compose versions inherited from the current Dictate upstream and JDK 17 in CI.

Typical debug build:

```bash
./tools/fetch-sherpa-onnx.sh
./gradlew :app:assembleDebug
```

Unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

GitHub Actions workflow:

```
.github/workflows/kapijuja-ci.yml
```

It performs:

1. a Dictate Cloud/Billing regression audit;
2. pinned native STT runtime download;
3. debug APK build;
4. debug APK artifact upload;
5. mandatory unit tests.

## Signing and updates

The project has release-signing configuration for:

- Package: `net.kapijuja.dictate`
- Expected stable alias: `kapijuja_voice_release`
- Intended key type: RSA-4096
- The actual keystore and passwords are **not stored in Git**.

The repository contains only:

```
keystore.properties.template
```

### Important verification rule

Repository history contains conflicting handoff statements about whether the external permanent JKS
was actually created and delivered. Git itself contains no JKS or certificate fingerprint, so the
key's existence cannot be proven from this repository alone.

Before the first signed release:

1. recover/locate the owner's existing Kapijuja signing backup and certificate fingerprint;
2. verify it with `keytool` / `apksigner`;
3. only generate a new permanent key if the owner explicitly confirms that no earlier permanent key
   exists and no distributed APK depends on it.

**Never generate a replacement key merely because the JKS is absent from Git.** Android in-place updates
require the same signing identity once a release has been distributed.

The repository already ignores:

```
*.jks
*.keystore
keystore.properties
```

## Branding

Dictate Kapijuja is intentionally visually distinct from Dictate.

- App name: Dictate Kapijuja
- Adaptive launcher icon: dark background with a gold **K**
- Project/About/Issues/Privacy/Changelog links point to this repository.
- Upstream attribution is preserved in `NOTICE` and source copyright headers.

The fork remains Apache-2.0.

## Recording recovery and audio history

Dictate Kapijuja treats captured speech as recoverable user data rather than disposable request input.

- Dictation history is enabled by default.
- Source audio retention is enabled by default for new installs.
- Retained audio lives only in the app's private `filesDir/dictate_history/` directory.
- Default pruning limits are 50 history entries, 30 days and 200 MB of retained audio.
- The History panel is placed beside Clipboard in the default Smartbar actions.
- A retained history recording can be sent to a recognizer chosen for that one replay without changing
  the global/default provider, enabling cross-provider "second opinion" recognition.
- During recording, the Smartbar trash/cancel button is non-destructive on the first tap. It opens an
  in-keyboard confirmation for five seconds while microphone capture continues; only the explicit second
  Delete action discards the recording. The legacy layout also requires a second confirmation tap.
- RecordingController streams PCM continuously into a uniquely named private cache WAV while the user
  speaks, so the already-captured portion is physically on disk during confirmation rather than living
  only in RAM. Recording/segment filenames are never reused across sessions.
- During `Transcribing…`, Kapijuja shows an explicit Stop control. Stopping cancels the in-flight
  provider request but keeps a private resend copy instead of deleting the recording.
- After Stop, the Smartbar offers Send again and explicit discard. For non-sensitive fields the rescue is staged under private persistent app storage, so an immediate process death can restore the Send again chip even if History had not finished its archive copy. Sensitive/password dictation stays transient and is never restored into another field. In addition to the resend
  copy, Stop/watchdog audio is force-archived as a recoverable History entry when History is enabled
  (never for incognito/password fields, and never duplicated for an existing History replay).
- The in-keyboard History panel now has a recognizer chooser for retained audio. A single saved recording
  can be replayed through OpenAI, Groq, Gemini, Deepgram, other configured built-ins, a custom endpoint
  or the installed on-device model.
- A History replay uses a one-shot provider override: it does **not** change the user's global/default
  transcription provider. After a successful replay, the entry's provider/model metadata is updated to
  describe the recognizer that actually produced the new text.
- A provider-independent no-progress watchdog now guards batch and long-form final transcription.
  Upload bytes, Soniox/AssemblyAI status polls and local sherpa-onnx decode steps refresh one heartbeat.
  If no meaningful progress occurs for the configured Request timeout, Kapijuja automatically cancels
  through the same retained-audio recovery path as manual Stop.
- Realtime keeps its existing short finalize watchdog; if realtime fails/finishes empty it falls back to
  batch transcription, which is then protected by the common no-progress watchdog.
- Generic transcription POSTs are limited to one application-level retry; OpenRouter remains at zero.
- Fault-injection unit tests now verify that cancelling a stalled HTTP transcription aborts the active OkHttp call without replaying the audio, and that invalid-key / quota responses are terminal rather than retried.
  Async status GET polling keeps its own safe retry budget because a GET does not re-upload audio or
  create another billable transcription job.
- Long-form keeps segment WAVs only in cache until the session reaches a terminal state. This temporary
  ownership is independent of permanent History retention and allows a Stop/watchdog to merge all
  segments back into one rescue WAV before cleanup.
- Batch requests also carry a generation ownership token and request-scoped temp filenames. A cancelled
  native/local job that returns late cannot clear the in-flight pointer, cancel the watchdog, or delete
  cache files belonging to a newer dictation/import/replay.

## Unicode fix made during the fork

The upstream `SelectionMetrics` test exposed inconsistent JDK 17 handling of ZWJ emoji via
`java.text.BreakIterator`.

Character/grapheme counting was changed to Java/Android regex `\\X`, which correctly treats extended
grapheme clusters such as family emoji as one visual character.

All unit tests passed after this change.

## Immediate roadmap

The next development work should proceed roughly in this order:

1. device-test the new recovery path with a real network cut: ordinary batch, OpenAI/Groq/Gemini/
   Deepgram, Soniox/AssemblyAI async, local STT, realtime fallback and long-form final drain;
2. test manual Stop/resend, process-death recovery through History, and the per-recording recognizer chooser;
3. produce the first **release** APK signed with the permanent Dictate Kapijuja key;
4. verify side-by-side installation with official Dictate;
5. consider a future "recognition variants" model if side-by-side comparison of several AI transcripts
   for the same retained audio is desired, without duplicating the audio file;
6. later decide whether to mirror upstream model/dictionary release assets under Kapijuja.

See **[NEXT_CHAT.md](NEXT_CHAT.md)** before modifying the project.


### Finite-state hardening

Dictate Kapijuja treats every asynchronous user operation as requiring a terminal state. Microphone stop
on both phone and Wear is bounded even if a vendor AudioRecord driver misbehaves, and Android
RecognitionService now has explicit terminal handling for busy, startup failure, missing credentials/model,
no audio, provider timeout, user cancellation, success and provider errors.
