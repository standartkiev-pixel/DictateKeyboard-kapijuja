# Kapijuja Voice — handoff for the next ChatGPT session

Date: 2026-09-09

## 0. Current status correction — 2026-09-09

The older sections below describe Cloud removal as unfinished because this file was originally written
mid-cleanup. That is no longer the repository state.

Verified on current `main`:

- Dictate Cloud client/server/Billing wiring is removed from executable/source code.
- The CI Cloud/Billing regression guard is fixed and passed.
- `tools/fetch-sherpa-onnx.sh`, debug APK build and unit tests passed on the clean baseline.
- The remaining generic `AudioReactiveCloudOrbView` is only a microphone visualization and must not be
  removed as "Dictate Cloud".
- Kapijuja now begins the recovery/audio-history phase:
  - source-audio history retention defaults ON for new installs;
  - default limits remain 50 entries / 30 days / 200 MB;
  - History is promoted beside Clipboard in the default Smartbar actions;
  - an explicit Stop control is being added to `Transcribing…`;
  - Stop preserves a private audio copy and offers Send again instead of destroying the recording.

Immediate next work after this checkpoint compiles cleanly:

### Recovery UX update — safe cancellation + per-recording AI

Additional Kapijuja-specific behaviour now lives on `main`:

- Smartbar recording cancel is two-stage. First tap only opens an in-keyboard confirmation for 5 seconds;
  recording continues. The user can return to recording or explicitly delete.
- Legacy dictation layout also requires a second cancel tap within 5 seconds.
- `RecordingController` already streams PCM directly into `cache/dictate_audio.wav` while recording;
  the confirmation does not suspend or destroy that file.
- Saved History audio now opens an in-keyboard recognizer chooser instead of a focusable dropdown.
- History replay accepts a one-shot provider id override and never changes
  `prefs.dictate.transcriptionProviderId`.
- Configured built-ins, custom endpoints and the installed local model can be selected. Unconfigured
  providers remain visible but disabled.
- A successful replay updates the history row's provider/model metadata to match the AI that actually
  produced the replacement transcript.

Do not replace the in-IME provider chooser with a normal Material `DropdownMenu`: focusable popups can
steal editor focus and cause the keyboard itself to hide.

The next large task remains the provider-independent no-progress watchdog for genuinely stuck
`Transcribing…`. Reuse the manual Stop/resend preservation path rather than inventing another recovery
mechanism.


1. verify the Stop/resend/history changes in CI;
2. add a provider chooser directly to a retained history recording, so the same audio can be sent to a
   different recognizer without globally changing provider first;
3. then implement the state-level no-progress watchdog so a genuinely stuck provider automatically
   lands in the same recoverable resend state.

## 1. Project and origin

Repository:
https://github.com/standartkiev-pixel/DictateKeyboard-kapijuja

Upstream:
https://github.com/DevEmperor/DictateKeyboard

Dictate Keyboard is an Apache-2.0 open-source fork/rebuild based on FlorisBoard. We imported the current upstream source with Git history so future upstream changes can still be compared and selectively merged.

Working product direction:
- New installed product name: **Kapijuja Voice** (branding work is not finished yet).
- Android application id already changed to **net.kapijuja.voice**.
- Goal: official Dictate and Kapijuja Voice must be installable side-by-side.
- Preserve upstream Apache-2.0 LICENSE and NOTICE attribution. Do not present Kapijuja Voice as the original Dictate product.

At the time of this handoff, GitHub reports both `main` and `kapijuja-bootstrap` at commit:
`cd23b55c2148e7e3a540ae993dccabda71a7dd7c`

Before making new edits, re-check both refs and continue from the actual newest commit.

## 2. External dependencies already investigated

There is no proprietary OpenAI/ChatGPT Android SDK required for core transcription. Online providers are reached through HTTP/WebSocket code, mainly with OkHttp.

Important Gradle/runtime stack from upstream:
- Kotlin 2.3.20
- Android Gradle Plugin 9.2.1
- compileSdk/targetSdk 36
- minSdk 26
- JDK 17
- OkHttp 5.3.0
- Jetpack Compose / AndroidX / Room / Coil / Coroutines / Serialization

On-device STT:
- sherpa-onnx 1.13.3
- ONNX Runtime Android 1.24.3

Their heavy JAR/SO binaries are intentionally NOT stored in Git. They are fetched by:
`tools/fetch-sherpa-onnx.sh`

That script downloads pinned AARs and verifies SHA-256. Keep this architecture; do not commit large native runtime binaries.

A small Silero VAD ONNX asset is committed in the app. Large Whisper/Parakeet/Canary/GigaAM/Kroko/SenseVoice models are downloaded separately.

Some downloadable models/dictionaries are still hosted as static release assets in the upstream DevEmperor GitHub releases. This is separate from Dictate Cloud. Do not mirror hundreds of MB yet unless there is a concrete reason; checksums already protect integrity.

## 3. Dictate Cloud / Google Play Billing investigation

Dictate Cloud was not a future placeholder. It is a real upstream paid-credit service:
- server code in `cloud/` (Cloudflare Worker)
- client package under `app/.../dictate/cloud/`
- dedicated provider preset pointing to `https://api.dictatekeyboard.com/v1/`
- wallet/recovery/balance fields
- Google Play Billing 8.x purchase flow
- Cloud settings/shop screen
- setup/onboarding branch
- low-credit Smartbar promo
- Cloud-specific error TOP_UP flow
- tests and What's New marketing UI

Important: Google Play Billing has no simple "API key" to replace. It is tied to the Android package/app in Play Console and product IDs. For Kapijuja Voice we decided to remove upstream Billing/Cloud completely for now. If monetization is ever added later, add Billing again using our own `net.kapijuja.voice` Play listing and our own product IDs.

### Already completed and committed

Verified in repository at handoff time:
- phone `applicationId = "net.kapijuja.voice"`
- Wear app applicationId changed to `net.kapijuja.voice`
- project version line reset to Kapijuja `0.1.0`, versionCode 1
- root project renamed to `KapijujaVoice`
- Google Play Billing version/library removed from `gradle/libs.versions.toml`
- `implementation(libs.android.billing.ktx)` removed from app Gradle
- `com.android.vending.BILLING` permission removed from AndroidManifest
- `ProviderRegistry.CLOUD` preset removed
- Cloud removed from built-in provider list/upload-limit routing
- upstream OpenRouter HTTP-Referer changed to our GitHub repository
- Dictate Cloud wallet/recovery/balance fields removed from `ProviderAccount`
- Cloud special-case credential rule removed from `ProviderAccount`

### Cloud removal still NOT finished

Continue carefully. Search the whole tree for:
`DictateCloud`
`ProviderRegistry.CLOUD`
`DictateCloudApi`
`DictateCloudScreen`
`dictate__cloud_`
`LOW_CREDIT`
`TOP_UP`
`api.dictatekeyboard.com`
`android.billing`
`com.android.vending.BILLING`

Likely remaining work:
1. Delete server directory `cloud/`.
2. Delete client `app/src/main/kotlin/.../dictate/cloud/`.
3. Remove `DictateCloudScreen.kt`.
4. Remove Cloud route/deep link from `Routes.kt`.
5. Remove Cloud choice from `SetupScreen.kt`; keep BYOK, on-device and self-hosted setup.
6. Remove Cloud row from `DictateProvidersScreen.kt`.
7. Remove Cloud entries from `SettingsSearchIndex.kt`.
8. Remove Cloud-specific imports and behavior from `DictateController.kt`:
   - `ErrorAction.TOP_UP`
   - out-of-credit special handling
   - `PromoKind.LOW_CREDIT`
   - balance refresh / low-credit warning
   - Cloud settings deep link
   - Cloud-only missing-credential wording
9. Remove TOP_UP/LOW_CREDIT rendering from `DictateSmartbarUi.kt`.
10. Remove `cloudLowCreditNudged` preference from `AppPrefs.kt`.
11. Remove old Cloud page/art from `WhatsNewTour.kt`.
12. Remove Cloud-specific setup/unit tests.
13. Remove `ic_dictate_cloud.xml` if no longer referenced.
14. After code compiles, optionally remove now-unused translated Cloud strings. Do not mass-delete strings before compile verification.
15. Remove/upsert old privacy/security/README claims about Dictate Cloud only after code removal is complete.

Do NOT remove generic uses of the word "cloud" where it simply means an online provider, cloud orb animation, network fallback, etc. Only remove Dictate Cloud product-specific code.

## 4. Branding and side-by-side installation — still to finish

Already done:
- package/application id: `net.kapijuja.voice`

Still pending:
- change launcher/app-visible name to **Kapijuja Voice**
- keep internal FlorisBoard namespaces unless there is a technical reason to rename them
- replace upstream project links (Issues, Privacy, Changelog, commit URLs, Play Store/PayPal promo links) with our project or remove them
- create a clearly distinct launcher icon: dark background + original gold/yellow **K** mark, not a modified copy of the Dictate microphone logo
- update Wear icon/name consistently
- update README/PRIVACY_POLICY/SECURITY and add Kapijuja derivative notice while retaining original LICENSE/NOTICE attribution
- decide later whether to rename repository; not required technically

## 5. Signing key — IMPORTANT, pending

A permanent Kapijuja release key was discussed, but at handoff time there is no verified completed tool action proving the JKS was actually created and safely delivered.

Next session should:
1. Generate a new dedicated RSA release keystore for Kapijuja Voice.
2. Use a stable alias, e.g. `kapijuja_voice_release`.
3. Store the JKS and passwords OUTSIDE Git.
4. Keep `keystore.properties` untracked.
5. Record SHA-256 certificate fingerprint.
6. Make a backup copy available to the project owner.
7. Configure GitHub Actions secrets only if signed CI releases are later needed.
8. Never regenerate the release key after users install releases, otherwise updates will no longer install over the previous version.

## 6. The main functional bug to solve: endless "Transcribing…"

User-observed problem:
After a network drop or provider failure, Dictate can sometimes remain showing "Transcribing…" apparently indefinitely instead of returning text or a usable error.

Upstream networking code was inspected.

### Batch/OpenAI-compatible path

`OpenAiCompatibleClient` uses OkHttp.

Important behavior:
- default request timeout historically/nominally 120 seconds
- `executeForBody(..., maxRetries = 3)` can mean first attempt + 3 retries = up to 4 attempts
- therefore a broken request can look "hung" for roughly 8 minutes even when each individual attempt has a timeout
- upstream issue #337 (2026-09-07) discovered a related large-upload retry/timeout problem
- upstream main added Settings -> AI providers -> Network -> Request timeout, range about 30–600 s, default 120 s
- large imports get larger budgets than normal dictation

Do not assume every long wait is a true infinite deadlock; distinguish excessive retries from a state-machine hang.

### Realtime path

`RealtimeClient` intentionally uses:
- WebSocket readTimeout = 0
- callTimeout = 0
- ping interval enabled

But `DictateController.stopRealtimeAndFinalize()` already has an approximately 1.2 s finalization watchdog and then cancels/falls back to batch when needed. So an ordinary realtime stop should not remain forever solely because the WebSocket has no read timeout.

### Desired fix

Add an UPPER-LEVEL state/job watchdog independent of individual HTTP/WebSocket implementation.

Requirements:
1. Whenever the UI enters `UiState.Transcribing`, there must be a guaranteed terminal path.
2. Track the active transcription coroutine/job and the underlying network call/session.
3. If the operation makes no meaningful progress for a configured period, cancel the job and underlying OkHttp Call/WebSocket/provider poll.
4. Force UI out of `Transcribing` into a clear recoverable Error state.
5. Preserve the recorded WAV/audio on failure so the user does not have to speak again.
6. Offer Retry/Resend using the retained audio.
7. Do not delete retained audio until success or explicit discard.
8. Consider a no-progress timeout rather than a simplistic total 60-second wall clock, because long recordings and slow self-hosted models can legitimately take longer.
9. For ordinary short dictation, a configurable 60–120 s no-progress ceiling is reasonable; respect the existing request-timeout setting.
10. A brief network transition can be debounced; do not immediately kill a request for a one-second Wi-Fi/mobile handover.
11. Avoid replaying billable/non-idempotent transcription POSTs too many times after ambiguous timeouts. For transcription, automatic retry count should probably be 0 or 1 rather than 3. The user can explicitly resend retained audio.
12. Ensure CancellationException is propagated as cancellation and does not become another automatic retry.

### Providers/pathways that must be audited

Do not fix only OpenAI. Verify terminal-state behavior for:
- OpenAI-compatible batch
- OpenRouter
- Gemini special transcription path
- Groq
- Mistral
- Deepgram
- ElevenLabs
- Soniox async polling
- AssemblyAI async polling
- custom/self-hosted endpoints
- realtime sessions
- local/on-device transcription
- fallback from online to local

For every provider path, answer:
- who owns the active job?
- what cancels the network/poll?
- what timeout applies?
- what happens to the WAV on failure?
- who sets `UiState.Error` or `UiState.Idle`?
- can any exception/callback path leave `UiState.Transcribing` forever?

A good implementation should centralize the "must eventually leave Transcribing" guarantee rather than scattering fixes across every provider.

## 7. Build/verification plan for next session

After finishing Cloud removal and branding:

1. Run a repository-wide search for Cloud/Billing leftovers.
2. Run `tools/fetch-sherpa-onnx.sh`.
3. Build at least:
   `./gradlew :app:assembleDebug`
4. Run relevant unit tests:
   `./gradlew :app:testDebugUnitTest`
5. Fix all compile errors caused by Cloud removal.
6. Add/repair GitHub Actions CI so every push builds/tests.
7. Install a debug/release candidate alongside official Dictate and verify both keyboards appear separately.
8. Verify providers:
   - OpenAI/BYOK
   - at least one alternative provider
   - custom server if practical
   - local model path
9. Reproduce network-drop case and confirm UI exits Transcribing.
10. Only after CI/build passes, merge/bootstrap changes cleanly and tag first Kapijuja version.

## 8. General development rule

Keep the project open source. Prefer small, reviewable commits.

Do not blindly delete upstream functionality just because it mentions a server. Preserve:
- BYOK providers
- custom/self-hosted server support
- offline models
- realtime support
- history/retained audio
- full FlorisBoard keyboard functionality

Remove only upstream product-specific monetization/service coupling that belongs to Dictate Cloud.

When changing upstream code, keep attribution headers unless the file is entirely replaced with new work. Add Kapijuja attribution for substantially modified/new files without erasing original authorship.

## Immediate next action

Start by finishing the remaining Dictate Cloud client/UI removal, then run a full build. Do not begin the Transcribing watchdog patch until the Cloud-removal branch compiles cleanly; otherwise two unrelated classes of errors will be mixed together.
