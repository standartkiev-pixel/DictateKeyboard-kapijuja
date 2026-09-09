# Kapijuja Voice — Developer / Next-Chat Handoff

**Date:** 2026-09-09  
**Repository:** `standartkiev-pixel/DictateKeyboard-kapijuja`  
**Main branch:** `main`

This is the current authoritative handoff for the project. Read this document before changing code.

## 1. Purpose

Kapijuja Voice is an Android voice keyboard forked from Dictate Keyboard, itself based on FlorisBoard.

Project goals:

- keep the full keyboard and voice/AI architecture;
- remove Dictate Cloud and Google Play Billing dependencies;
- use BYOK online providers and on-device STT;
- make long dictation safe under bad networks/provider failures;
- never destroy a long recording through one accidental/nervous tap;
- retain recoverable audio and allow re-transcription through another AI.

Current app identity:

- Product: **Kapijuja Voice**
- Full name: **Kapijuja Voice Keyboard**
- applicationId: `net.kapijuja.voice`
- versionName: `0.1.0`
- versionCode: `1`
- main development branch: `main`
- retained historical branch: `kapijuja-bootstrap`

Do not mass-rename FlorisBoard namespaces/classes merely for branding.

## 2. Verified baseline

Last verified baseline:

`ba315c1b3ed7eb26a8b8f835287e51de91bb7eb1`

GitHub Actions run:

`34337994539`

Result: **SUCCESS**

Passed:

- checkout
- Temurin JDK 17 setup
- Dictate Cloud / Billing regression audit
- pinned sherpa-onnx runtime fetch
- debug APK build
- debug APK artifact upload
- unit tests

Debug artifact:

- name: `kapijuja-voice-debug`
- artifact id: `10098628219`
- approx. zip size: 73 MB
- digest: `sha256:909b716cdccc34199617b48ff3704a8e514a6a542543de15d3705084e9d0aa0d`

Do not rediscover whether the current recovery/watchdog architecture compiles. It already passed CI at this baseline.

## 3. Upstream licensing / attribution

Preserve:

- Apache-2.0 license;
- LICENSE / NOTICE;
- upstream Dictate Keyboard attribution;
- FlorisBoard attribution;
- existing source copyright headers.

Do not delete attribution while branding user-facing UI.

## 4. Dictate Cloud / Billing removal

Dictate Cloud and Google Play Billing have been removed from executable/source wiring.

Removed or eliminated from live wiring:

- Dictate Cloud server/client integration;
- DictateCloud API/screen/routes/onboarding;
- Cloud provider branches;
- Cloud credit/balance UI;
- TOP_UP / LOW_CREDIT behavior;
- Google Play Billing;
- `com.android.vending.BILLING`;
- billing dependencies.

Important exception:

`AudioReactiveCloudOrbView.kt` is just a visual microphone/cloud animation. It is **not** the old Dictate Cloud product. Do not delete it because of its filename.

CI includes a regression grep to catch accidental Cloud/Billing reintroduction. Historical documentation is intentionally excluded from that grep.

## 5. Branding

Already changed substantially:

- Kapijuja Voice product identity;
- own applicationId;
- root project name `KapijujaVoice`;
- dark launcher background;
- gold K foreground;
- monochrome adaptive icon;
- Kapijuja project links in About/Issues/Privacy/Changelog;
- upstream payment/store links removed or redirected.

Do not blindly rename:

- `dev.patrickgold.florisboard`;
- `DictateController`;
- protocol paths;
- Wear synchronization paths;
- internal class identifiers.

Some user-visible “Dictate” strings remain in legacy/Wear/locales. Clean those later as a dedicated branding task.

## 6. Signing

Release signing is already wired through optional, untracked `keystore.properties`.

Template exists:

`keystore.properties.template`

Expected alias:

`kapijuja_voice_release`

Warning: repository text claims a permanent Kapijuja signing identity, but the repository itself cannot prove the external JKS exists.

**Do not generate a replacement key automatically.**

Before first signed release verify:

- whether the permanent JKS already exists;
- whether a certificate fingerprint was recorded elsewhere;
- whether any Kapijuja APK has already been signed with that key.

Generating another key after a signed install would break normal Android updates.

## 7. Native STT architecture

Current native stack:

- sherpa-onnx 1.13.3
- ONNX Runtime Android 1.24.3

Heavy runtime files are not committed. CI obtains them via:

`tools/fetch-sherpa-onnx.sh`

with pinned SHA-256 verification.

Do not commit large runtime/model blobs.

## 8. Unicode correctness fix

`SelectionMetrics` grapheme counting was changed from `java.text.BreakIterator` to regex `\X`.

Reason: JDK 17 behavior did not count ZWJ family emoji consistently.

Unit tests pass with the new implementation.

## 9. Kapijuja UX invariant

Core product rule:

> One accidental or nervous tap must never destroy a long recording.

Captured audio is recoverable user data, not disposable HTTP request input.

Under failure the preferred flow is:

1. stop/cancel the failing work;
2. preserve audio;
3. expose **Send again**;
4. allow another recognizer;
5. keep a recovery path in History.

## 10. Safe recording cancellation

Normal Smartbar:

The first Cancel/Trash tap is non-destructive.

It:

- does not stop the microphone;
- does not delete the WAV;
- opens an in-keyboard confirmation;
- keeps recording continuously.

Confirmation offers:

- Mic / Continue;
- explicit red Delete.

It auto-dismisses after 5 seconds if untouched.

Only explicit second Delete actually discards.

Legacy layout has less room, so it uses two-step confirmation:

- first tap arms deletion;
- second tap within 5 seconds deletes.

Do not replace this with a generic focusable modal/dropdown without testing IME focus. A focusable popup may hide the keyboard.

## 11. Recording file ownership

Old upstream behavior reused:

`cache/dictate_audio.wav`

That was unsafe because a cancelled native/local job could finish late and delete a pathname already reused by a new recording.

Now each RecordingController/session and segment gets a unique cache WAV.

Do **not** restore a fixed `dictate_audio.wav`.

Also unique/request-scoped now:

- History replay temp files;
- imported-audio handoff files;
- trimmed audio;
- speed-up copies;
- packed upload files;
- long-form merged rescue audio.

Each batch transcription also has a generation ownership token. Late cleanup from an older cancelled job may not clear state/watchdogs/files of a newer request.

## 12. Manual Stop during Transcribing

An explicit Stop control exists during `Transcribing…`.

Manual Stop:

- stops the provider job;
- preserves audio first;
- creates a unique rescue copy;
- cancels coroutine / OkHttp Call;
- enters a recoverable UI state;
- exposes **Send again**;
- has explicit discard separately.

Messages include:

- `Stopped · recording kept`
- `Остановлено · запись сохранена`

Watchdog message:

- `No progress · recording kept`
- `Нет прогресса · запись сохранена`

## 13. History / audio archive

History is now a first-class recovery surface.

For new installs:

`historyAudioRetention = true`

Default limits:

- 50 entries
- 30 days
- 200 MB

Persistent retained audio location:

`filesDir/dictate_history/`

History is promoted beside Clipboard in the default Smartbar action ordering.

Existing custom user layouts are preserved; migration moves History only if it is still in the recognizable old default tail location.

History supports:

- playback;
- export;
- pin;
- delete;
- insert transcript;
- re-transcribe.

Manual Stop/watchdog also force-archive a recoverable History item when History is enabled.

Never archive sensitive/password/incognito dictation.

Do not duplicate a History row when re-transcribing audio that already belongs to an existing History entry.

## 14. Re-transcribe saved audio with another AI

A saved History recording can be sent through a different recognizer.

The chooser is implemented **inside the keyboard History panel**, not as a normal focusable Material DropdownMenu.

Reason: a popup can steal editor focus and make the IME disappear.

The chooser shows transcription-capable providers including:

- OpenAI;
- Groq;
- Gemini;
- Deepgram;
- other built-ins;
- custom/self-hosted endpoints;
- installed local model.

Unconfigured providers remain visible but disabled.

History replay uses a **one-shot provider override**.

It does not change global:

`prefs.dictate.transcriptionProviderId`

Example:

- global provider is OpenAI;
- one saved WAV is replayed with Groq;
- next ordinary dictation still uses OpenAI.

Successful replay updates not only text but provider metadata:

- providerId;
- providerName;
- model;
- language.

Future optional design: **Recognition Variants** — one audio asset linked to several transcripts (OpenAI/Groq/Gemini) without duplicating the WAV.

## 15. No-progress watchdog

Original user-facing bug:

`Transcribing…` could remain visible for many minutes after network/provider failure.

A provider-independent no-progress watchdog is now implemented in `DictateController`.

It does not simply measure total request duration. It tracks meaningful heartbeat progress.

Heartbeat refreshes on:

- upload bytes;
- Soniox async status responses;
- AssemblyAI async status responses;
- local sherpa-onnx decode/segmentation progress;
- long-form segment transcription progress.

The existing setting is reused:

Settings → AI providers → Network → Request timeout

Range:

30–600 seconds

Default:

120 seconds

Meaning now:

“How many seconds may pass with no meaningful progress before recovery triggers?”

On timeout the watchdog does **not** set UiState directly.

It routes through the same safe recovery mechanism as manual Stop:

- preserve audio;
- cancel provider coroutine/network call;
- surface Send again;
- archive recovery audio to History where allowed.

Do not create a second timeout slider without a strong reason.

## 16. Realtime

Realtime already has its own short finalize watchdog (~1.2 s).

On finalize it:

- waits briefly for provider final words;
- force-closes/cancels the session;
- if the stream failed or transcript is empty, falls back to normal batch transcription.

That batch fallback is now covered by the common no-progress watchdog.

Do not layer another unrelated coarse timeout over realtime unless a real device test proves it is needed.

## 17. Long-form segmented dictation

Long-form can have several segment jobs in flight simultaneously.

Important changes:

- every segment WAV stays temporarily owned in cache until terminal success/cancel;
- this temporary cache retention is independent of permanent History retention;
- final drain starts the common watchdog;
- segment provider/local progress refreshes heartbeat.

On Stop/watchdog during final drain:

- all segment jobs are cancelled;
- any recorder tail is finalized;
- segment WAVs are ordered;
- `AudioConcat` creates one rescue WAV;
- provisional preview is removed;
- Send again is offered;
- a recoverable History row is stored when allowed.

A hidden outer long-form retry was removed. Retry policy now belongs to the provider layer only.

## 18. Retry policy

Old generic `executeForBody` behavior allowed:

initial request + up to 3 application retries.

For paid STT this could mean repeated WAV uploads, long waits and ambiguous duplicate billing.

Current transcription policy:

Generic STT:

`TRANSCRIPTION_NETWORK_MAX_RETRIES = 1`

That means initial + at most one application-level retry.

OpenRouter:

`OPENROUTER_TRANSCRIPTION_MAX_RETRIES = 0`

Do not restore 3 retries for billable/non-idempotent STT POSTs.

Async status GET polling may keep a separate retry budget because it does not upload audio again or create a second transcription job.

## 19. Cancellation rules

`CancellationException` must propagate.

Do not map coroutine cancellation into a retryable provider failure.

Network execution uses cancellable OkHttp async calls. Cancellation triggers `Call.cancel()`.

This is required so the user-visible Stop actually stops the network request rather than only hiding the UI.

## 20. Tests

Network tests now cover:

- generic transcription emits liveness heartbeat;
- generic transcription performs at most one application-level retry;
- OpenRouter never replays a billable STT POST.

Other project tests include the Unicode/grapheme fix and existing provider/network behavior.

Verified CI at `ba315c1b…` is fully green.

## 21. GitHub Actions

Workflow:

`.github/workflows/kapijuja-ci.yml`

Environment:

- Ubuntu runner
- Temurin JDK 17
- `actions/checkout@v5`
- `actions/setup-java@v5`

Pipeline:

1. Cloud/Billing audit
2. fetch pinned native STT runtime
3. build debug APK
4. upload debug APK
5. unit tests

`concurrency.cancel-in-progress: true`

Therefore intermediate runs can be cancelled when several small commits are pushed. Judge the last run for the current HEAD.

## 22. Do not do these things

Do not:

- reintroduce Dictate Cloud;
- reintroduce Billing;
- delete `AudioReactiveCloudOrbView` merely because its name contains Cloud;
- mass-rename FlorisBoard namespaces;
- mass-rename internal Dictate classes;
- alter Wear/internal protocol paths casually;
- restore fixed `dictate_audio.wav`;
- restore fixed request temp filenames;
- restore maxRetries=3 for STT POSTs;
- swallow `CancellationException`;
- delete rescue audio during Manual Stop;
- return to one-tap destructive recording cancellation;
- generate a new signing key before verifying the old one;
- commit heavy sherpa/model blobs;
- remove upstream LICENSE/NOTICE/attribution.

## 23. What to do next

The next phase should be **real-device fault testing**, not another architecture rewrite.

### A. Install latest green debug APK

Use the latest green artifact for the current baseline.

### B. Safe Cancel test

- record 30–120 seconds;
- tap Cancel once;
- verify mic/record timer continue;
- verify auto-dismiss;
- verify Continue;
- verify only explicit second Delete discards.

### C. Manual Stop test

- record;
- send;
- press Stop while Transcribing;
- verify request stops;
- verify “recording kept”;
- verify Send again;
- verify recovery entry in History.

### D. Network-cut matrix

Test:

- OpenAI
- Groq
- Gemini
- Deepgram
- Soniox
- AssemblyAI
- custom/self-hosted

For each:

1. record;
2. send;
3. disable connectivity during upload/processing;
4. wait for watchdog;
5. verify recoverable terminal state;
6. verify History;
7. restore network;
8. Send again;
9. replay same History WAV using another AI.

### E. Local STT

- test Stop during local decode;
- immediately start/send another dictation;
- verify late native cleanup cannot damage the new request.

### F. Realtime

- normal finalize;
- forced stream failure;
- verify fallback to batch;
- verify batch watchdog if necessary.

### G. Long-form

- create several segment cuts;
- Stop during final Transcribing;
- verify all jobs stop;
- verify merged rescue WAV contains all spoken segments in order;
- Send again and verify full transcript.

### H. Process-death recovery

- trigger Manual Stop/watchdog;
- do not resend;
- kill/restart app;
- verify recoverable History audio remains.

### I. Privacy

In password/incognito/sensitive input:

- recovery audio must not be archived in History.

## 24. After device tests

If fault tests are clean:

1. verify the existing permanent signing JKS/fingerprint;
2. build first signed release APK;
3. verify side-by-side installation with official Dictate;
4. verify update-over-existing Kapijuja install;
5. clean remaining user-facing Dictate branding;
6. consider Recognition Variants.

## 25. Reading order for the next chat/developer

Read in this order:

1. `DEVELOPER_HANDOFF.md`
2. `NEXT_CHAT.md`
3. `PROJECT_HANDOFF.md`
4. `README.md`
5. `SECURITY.md`
6. `PRIVACY_POLICY.md`

If documentation conflicts:

- current `main` wins;
- the latest successful CI wins;
- this newer handoff wins over older mid-refactor notes.

## 26. Current checkpoint

Verified commit:

`ba315c1b3ed7eb26a8b8f835287e51de91bb7eb1`

Verified Actions run:

`34337994539`

Status:

**SUCCESS**

Debug artifact:

`kapijuja-voice-debug`

Artifact id:

`10098628219`

Digest:

`sha256:909b716cdccc34199617b48ff3704a8e514a6a542543de15d3705084e9d0aa0d`

End of handoff.
