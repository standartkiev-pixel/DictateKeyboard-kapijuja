# Dictate Kapijuja — Developer Guide

This guide is the compact engineering contract for continuing the fork.

For full historical context and the exact 2026-09-09 handoff, read
[`FULL_PROJECT_HANDOFF_2026-09-09.txt`](FULL_PROJECT_HANDOFF_2026-09-09.txt) first.

## 1. Repository and baseline

Repository: `standartkiev-pixel/DictateKeyboard-kapijuja`

Upstream: `DevEmperor/DictateKeyboard` (FlorisBoard-derived)

Android application id: `net.kapijuja.dictate`

Verified recovery/watchdog source baseline:

- commit: `ba315c1b3ed7eb26a8b8f835287e51de91bb7eb1`
- GitHub Actions run: `34337994539`
- result: **success**
- debug artifact: `dictate-kapijuja-debug`
- Cloud/Billing audit, sherpa runtime fetch, debug APK build, artifact upload and unit tests all passed.

Documentation commits after that checkpoint do not change the verified recovery source architecture.

## 2. Read before editing

Read, in this order:

1. `FULL_PROJECT_HANDOFF_2026-09-09.txt`
2. this file
3. `NEXT_CHAT.md`
4. `README.md`
5. `PROJECT_HANDOFF.md` for historical context

If old handoff prose conflicts with current source and passing CI, current source + passing CI wins.

## 3. Fork-specific engineering style

Kapijuja changes should be easy to audit against upstream.

When modifying upstream-derived code, add comments that explain:

- what changed;
- why Kapijuja differs;
- which user failure/race the code protects;
- what future developer must not casually remove;
- whether an inherited name/package/protocol is intentionally left unchanged.

Do not remove Apache-2.0 notices or upstream attribution.

Do not mass-rename internal FlorisBoard/Dictate classes or namespaces just for branding.

## 4. Dictate Cloud / Billing invariant

Dictate Cloud and Google Play Billing were intentionally removed.

Do not reintroduce:

- `DictateCloud`
- `DictateCloudApi`
- `ProviderRegistry.CLOUD`
- `api.dictatekeyboard.com`
- `com.android.vending.BILLING`
- old low-credit/top-up flows

The CI grep is the regression guard.

`AudioReactiveCloudOrbView` is a microphone visualization, not the removed Cloud product.

## 5. Recording safety contract

A long user recording is valuable data.

### While recording

One accidental tap must not destroy it.

Normal Smartbar:

- first Trash/Cancel tap only opens a five-second in-IME confirmation;
- microphone capture continues;
- explicit second Delete is destructive;
- timeout dismisses confirmation and recording continues.

Legacy UI:

- first tap arms deletion;
- second tap within five seconds confirms.

Do not replace this with one-tap deletion.

### While transcribing

Stop means **stop provider work**, not **delete speech**.

Manual Stop must:

1. preserve/copy the active audio first;
2. cancel coroutine / OkHttp work;
3. expose Send again;
4. leave explicit discard as a separate user action;
5. archive a recovery copy into History when allowed.

## 6. Unique file ownership is mandatory

Never reuse a fixed request pathname such as `dictate_audio.wav`.

Every recording/request/derived temp file must be uniquely owned.

Reason: cancelled native/local jobs can return late. An old cleanup must be physically incapable of
removing a newer request's file.

This rule applies to:

- microphone WAV
- long-form segments
- history replay temp
- import handoff
- trim output
- speed-up output
- packed upload output
- merged long-form rescue

Batch transcription also has generation ownership. An old job may clear only its own shared state.

Do not remove generation checks unless the late-cancellation race is proven impossible.

## 7. History and recovery

Permanent history audio directory:

`filesDir/dictate_history/`

New-install defaults:

- audio retention ON
- 50 entries
- 30 days
- 200 MB

History is intentionally placed near Clipboard.

History supports playback/export/pinning/deletion/re-transcription.

Stopped/stalled audio should also be recoverable after process death when History is enabled.

Never archive password/incognito dictation.

Do not duplicate an already-existing History replay into another recovery row.

## 8. Cross-provider replay

A saved recording may be sent to another recognizer directly from History.

The provider selection is **one-shot** for that replay.

It must **not** change the global/default provider.

After successful replay, update provider/model/language metadata together with the transcript.

The chooser stays inside the IME panel. Avoid focusable Material popup/dropdown UI that can steal
editor focus and hide the keyboard.

## 9. No-progress watchdog contract

The watchdog lives above individual providers in `DictateController`.

It measures **absence of meaningful progress**, not total elapsed request time.

Heartbeat sources include:

- upload bytes;
- Soniox async poll responses;
- AssemblyAI async poll responses;
- local sherpa-onnx decode progress;
- long-form segment progress.

The existing Request timeout setting is the user-facing no-progress budget:

- minimum 30 s
- maximum 600 s
- default 120 s

On watchdog expiry, call the same retained-audio Stop recovery path.
Do not directly force an arbitrary UiState and leave the underlying request alive.

## 10. Cancellation rules

`CancellationException` must propagate.

Never:

- convert cancellation into a retry;
- swallow it as an ordinary provider error;
- keep an OkHttp call alive after UI cancellation;
- let an old native/local completion clear a new request's state.

## 11. Retry policy

Speech-to-text POSTs can be billable/non-idempotent.

Current application-level policy:

- ordinary STT: initial attempt + max **one** retry;
- OpenRouter STT: **zero** retries;
- safe async status GETs may keep their explicit retry budget.

Do not restore the generic old three-retry behavior for transcription POSTs.

Do not add another outer retry in long-form.

## 12. Realtime

Realtime already has its own short finalization watchdog (about 1.2 s).

If realtime fails or yields no transcript, it falls back to batch using the captured WAV.

That batch request is then protected by the common no-progress watchdog.

Do not add a competing realtime timeout architecture unless testing proves the existing one is wrong.

## 13. Long-form segmented dictation

Long-form may have several background transcription jobs at once.

During a session:

- keep segment WAVs in cache until terminal cleanup;
- this temporary ownership is independent of permanent History retention;
- final drain starts the common watchdog;
- segment progress refreshes heartbeat.

On Stop/watchdog:

- cancel all tracked segment jobs;
- finalize any active recorder tail;
- concatenate segment WAVs in speaking order;
- clear provisional partial text;
- expose one merged rescue WAV;
- block late old segment results from reappearing.

Do not cancel only one `transcribeJob` and pretend long-form is stopped.

## 14. Local STT

Local STT uses sherpa-onnx.

Native calls are not always instantaneously cancellable.

That is why:

- local decode emits liveness heartbeat;
- request generation ownership exists;
- unique files exist.

A slow local model should stay alive while reporting progress.
A late cancelled model must not touch a newer request.

## 15. Signing

The repository contains signing configuration and `keystore.properties.template`.

Expected alias: `kapijuja_voice_release`.

The actual JKS is external and not verifiable from Git.

Important rule:

**Do not generate a new release key simply because the key is not in the repository.**

First recover/verify the owner's existing JKS backup and SHA-256 certificate fingerprint.

Only create a new permanent key if the owner explicitly confirms no earlier permanent key exists and
no distributed APK depends on it.

Never commit JKS/passwords.

## 16. Branding

Brand user-visible surfaces as Dictate Kapijuja.

Do not rename internal classes/protocol paths merely to eliminate the word Dictate.

Remaining cleanup likely exists in Wear/legacy strings.

Treat branding cleanup as a separate reviewable stage.

## 17. CI

Workflow: `.github/workflows/kapijuja-ci.yml`

It uses `cancel-in-progress: true`.

Therefore a run marked cancelled after a newer commit is expected and is not evidence the source failed.

A final stage is complete only when the latest intended checkpoint has:

- Cloud/Billing audit PASS
- sherpa runtime fetch PASS
- debug APK build PASS
- artifact upload PASS
- unit tests PASS

Never "fix" CI by disabling a meaningful test.

## 18. Device fault-test matrix — next major stage

Before another architectural refactor, test the current APK on a real device.

For each available provider/path:

- OpenAI
- Groq
- Gemini
- Deepgram
- Soniox
- AssemblyAI
- ElevenLabs if configured
- custom/self-hosted
- local model
- realtime
- long-form

Test:

1. normal success;
2. manual Stop during Transcribing;
3. Wi-Fi/mobile-data loss during upload/wait;
4. watchdog terminal recovery;
5. Send again after network restoration;
6. History process-death recovery;
7. replay through a different provider;
8. accidental first Cancel tap while recording;
9. rapid Stop -> immediately start a new recording;
10. long-form Stop while several segments are still in flight.

Expected invariant for all recoverable failure cases:

**the user never has to re-speak the recording merely because transport/provider work failed.**

## 19. Likely next fixes

Only after real-device testing:

1. fix concrete failures exposed by the test matrix;
2. resolve release-key existence;
3. build/sign first release APK;
4. verify side-by-side install with official Dictate;
5. finish remaining user-visible branding;
6. optionally design "recognition variants" so several AI transcripts can reference one audio asset.

## 20. Important files

- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/audio/RecordingController.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/LocalTranscriptionProvider.kt`
- `lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/OpenAiCompatibleClient.kt`
- `lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/ProviderModels.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/data/history/DictateHistory.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/ui/DictateHistoryLayout.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/ui/DictateSmartbarUi.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/ui/LegacyDictateLayout.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
- `.github/workflows/kapijuja-ci.yml`

## 21. Final developer rule

If a proposed cleanup makes the code shorter by removing recovery state, unique file ownership,
generation checks, two-step destructive confirmation, or retained-audio behavior, assume it is
reintroducing a previously discovered data-loss race until proven otherwise.
