# NEXT CHAT — Kapijuja Voice development handoff

This file is the authoritative handoff for continuing development in a new ChatGPT conversation.

**Read this file before changing anything. The purpose is to avoid rediscovering work already done.**

---

## 1. Repository

GitHub repository:

`standartkiev-pixel/DictateKeyboard-kapijuja`

Upstream source:

`DevEmperor/DictateKeyboard`

Upstream origin is Dictate Keyboard, which is itself a FlorisBoard-based project.

At the time this handoff was written, the verified Kapijuja bootstrap state had been fast-forwarded
into `main`.

Important known commits from the bootstrap work:

- original upstream baseline copied into our repository:
  `ab0357c24105372fd14ee951c82bcadea8cfb7fa`
- verified Kapijuja bootstrap head before final documentation:
  `cd23b55c2148e7e3a540ae993dccabda71a7dd7c`

There is also a `kapijuja-bootstrap` branch retained from the migration work.

Do not assume the above commit remains HEAD forever; always inspect current `main` first.

---

## 2. Why this fork exists

The original Dictate Keyboard is an excellent open-source Android keyboard with strong AI voice
dictation features. The user specifically prefers it over alternatives because it supports high
quality provider-based speech transcription instead of locking the user to a weaker proprietary
speech engine.

The main reasons for this fork are:

1. preserve the full Dictate/FlorisBoard keyboard;
2. keep direct BYOK access to good AI/STT providers;
3. remove dependence on the original Dictate Cloud prepaid-minute service;
4. make the project independently installable and independently signable;
5. fix practical UX problems, especially endless/stuck `Transcribing…` after network/provider failure;
6. keep the project open source so improvements can flow both ways.

---

## 3. Project identity — DO NOT CHANGE casually

Current product identity:

- visible app name: **Kapijuja Voice**
- full product name: **Kapijuja Voice Keyboard**
- Android application id: **`net.kapijuja.voice`**
- root Gradle project name: **`KapijujaVoice`**
- current fork version line started at **`0.1.0` / versionCode `1`**

The new application id is critical because it allows official Dictate and Kapijuja Voice to be
installed simultaneously.

Debug/beta builds have their own applicationId suffixes and therefore do not replace the release app.

### Important

Do **not** change `net.kapijuja.voice` in routine development.

A package-id change creates a different Android application and breaks the intended update/install
identity.

---

## 4. Signing — CRITICAL

A permanent Kapijuja Voice release key was created separately from the Git repository.

Known signing identity:

- alias: **`kapijuja_voice_release`**
- key type: **RSA-4096**
- certificate validity was checked with `keytool`
- the keystore/passwords are intentionally NOT in Git

A private backup ZIP was delivered to the user during the bootstrap conversation. It contains:

- the Kapijuja Voice `.jks` release keystore;
- a usable `keystore.properties`;
- password/fingerprint backup information.

Repository-safe template:

`keystore.properties.template`

Git ignore already covers:

- `*.jks`
- `*.keystore`
- `keystore.properties`

### DO NOT DO THIS

**Never generate a new signing key just because the old one is not immediately visible in a new chat.**

Ask the user for the saved Kapijuja signing backup if release signing is required.

Every future release intended to update the existing Kapijuja Voice installation must use the same
release key.

---

## 5. Dictate Cloud and Google Play Billing were removed

This was a major part of the bootstrap.

The original project contained a real prepaid service, not merely an unused placeholder.

It included:

- Dictate Cloud backend;
- wallet/account token;
- prepaid minute packs;
- recovery codes;
- device/account management;
- balance refresh;
- low-credit warnings;
- Google Play Billing purchases;
- server-side usage metering;
- Cloud-specific transcription/rewording routing.

### Removed server project

The entire root:

`cloud/`

was removed.

That included TypeScript/Cloudflare worker code, migrations, admin tools, billing/order handling,
wallet handling, metering, routes, notifications and server configuration.

### Removed Android-side classes/features

Important removed components included:

- `DictateCloud.kt`
- `DictateCloudApi.kt`
- `DictateCloudBilling.kt`
- `DictateCloudPack.kt`
- `DictateCloudScreen.kt`
- Cloud navigation route/deep link
- Cloud setup choice
- Cloud provider row
- Cloud wallet/balance/recovery state
- low-credit promo and top-up actions
- Cloud-specific tests and icons
- Cloud localized strings

The `ProviderRegistry.CLOUD` preset was removed.

Cloud-specific state was removed from `ProviderAccount`.

### Google Play Billing removal

Removed:

- Billing version entry from Gradle version catalog;
- `com.android.billingclient:billing-ktx`;
- Billing implementation dependency;
- Android manifest permission:
  `com.android.vending.BILLING`.

No Google Play Billing is currently required for Kapijuja Voice.

If monetization is ever added later, implement it as a **new Kapijuja system** associated with
`net.kapijuja.voice` and new product IDs. Do not revive upstream Dictate Cloud product IDs.

---

## 6. What remains and must keep working

The removal of Dictate Cloud was deliberately isolated from normal providers.

Kapijuja Voice should retain:

- complete FlorisBoard-derived typing keyboard;
- suggestions/autocorrect/glide/etc. inherited from Dictate/FlorisBoard;
- voice recording;
- history;
- rewording/prompts;
- floating button/accessibility path;
- long-form dictation;
- realtime transcription where supported;
- Wear OS support;
- BYOK online providers;
- custom OpenAI-compatible/self-hosted endpoints;
- local/on-device transcription.

Known provider architecture includes services such as:

- OpenAI
- Groq
- OpenRouter
- Gemini
- Anthropic
- Together
- DeepInfra
- Mistral
- Soniox
- ElevenLabs
- Deepgram
- AssemblyAI
- xAI
- DeepSeek
- SiliconFlow
- Ollama
- local/on-device

Do not reduce this list unless there is a concrete reason.

---

## 7. External libraries / SDK findings

One of the first investigations was whether this project secretly depended on a closed SDK.

It does not.

Online providers are primarily reached directly over HTTP/WebSocket rather than through proprietary
provider SDKs.

Important dependency architecture:

- OkHttp network layer;
- AndroidX / Jetpack Compose / Room / Kotlin libraries;
- sherpa-onnx for local STT;
- ONNX Runtime Android;
- Google Play Services Wearable for Wear OS;
- KLIPY API for GIF functionality when configured by the user.

At the time of investigation the upstream project was around:

- Kotlin 2.3.20
- Android Gradle Plugin 9.2.1
- Compose BOM 2026.03.01
- OkHttp 5.3.0
- Room 2.8.4
- Coil 3.4.0
- sherpa-onnx 1.13.3
- ONNX Runtime Android 1.24.3
- JDK 17 in CI

Treat Gradle files as authoritative if versions later change.

---

## 8. sherpa-onnx / ONNX binaries

Large native runtime binaries are intentionally not committed.

Use:

`tools/fetch-sherpa-onnx.sh`

The script downloads pinned artifacts and verifies SHA-256 checksums.

This design should be preserved.

Do not commit giant extracted `.so`, AAR or runtime binary trees unless there is an exceptional,
documented reason.

A small Silero VAD ONNX asset remains in the repository and is expected.

---

## 9. Static upstream model/dictionary assets remain intentionally

Some downloadable assets still come from original Dictate GitHub Release Assets.

This is currently intentional and is NOT the same thing as using Dictate Cloud.

Relevant files include:

- `app/.../LocalModelCatalog.kt`
- `BigramCatalog.kt`
- `GlideDictionaryCatalog.kt`
- `PinyinPackManager.kt`

These cover downloadable speech models / dictionaries / language packs.

Do not blindly replace the URLs.

Future option: mirror the exact artifacts under Kapijuja GitHub Releases and then switch catalogs,
while preserving expected names, versions and hashes.

---

## 10. Branding work already done

Installed application branding was separated from Dictate.

Current branding:

- dark launcher background;
- gold **K** foreground mark;
- monochrome K adaptive icon;
- Kapijuja Voice name;
- Kapijuja GitHub links in About/Issues/Privacy/Changelog.

Old upstream Play Store/PayPal-style user-facing links were removed or redirected away from upstream
commercial destinations.

Do not remove upstream copyright/license attribution from source files.

`NOTICE` has Kapijuja derivative notice followed by original Dictate/FlorisBoard attribution.

License remains Apache License 2.0.

---

## 11. README / privacy / security files

Project-owned documentation was created/rewritten:

- `README.md`
- `PRIVACY_POLICY.md`
- `SECURITY.md`
- `NOTICE`
- this file: `NEXT_CHAT.md`

Privacy policy explicitly states that Kapijuja does not operate its own transcription proxy/account/
payment backend and that selected online providers receive data directly when used.

Keep these statements accurate if architecture changes later.

---

## 12. CI

Workflow:

`.github/workflows/kapijuja-ci.yml`

The workflow currently:

1. checks out the repository;
2. sets up JDK 17;
3. audits that Dictate Cloud/Billing wiring has not returned;
4. fetches pinned sherpa/ONNX native runtime;
5. builds debug APK;
6. uploads debug APK as an Actions artifact;
7. runs mandatory debug unit tests.

Concurrency/cancel-in-progress was added so old branch builds do not create a large queue.

### Dictate Cloud regression audit

CI greps for things such as:

- `api.dictatekeyboard.com`
- `com.android.vending.BILLING`
- `billing-ktx`
- `DictateCloud`
- `dictate__cloud_`

Documentation and the workflow file itself are excluded where necessary so descriptions do not trip
the guard.

Do not remove this audit unless replacing it with an equally strong regression check.

---

## 13. Verified CI result

Before merging the bootstrap into `main`, a full control run succeeded:

- Dictate Cloud/Billing audit: PASS
- pinned native STT fetch: PASS
- debug APK build: PASS
- debug APK artifact upload: PASS
- unit tests: PASS

The final mandatory-test control run also succeeded before `main` was fast-forwarded.

A debug APK artifact was successfully produced (roughly 73 MB zipped artifact at that time).

This means Cloud removal did not prevent the Android app from compiling and the test suite was green.

---

## 14. Unicode/grapheme bug discovered and fixed

The upstream unit suite had one failing test:

`SelectionMetricsTest > an emoji built from several is still one character`

The existing code used:

`java.text.BreakIterator.getCharacterInstance()`

Under the CI JDK 17 environment it did not reliably count a ZWJ family emoji as one extended grapheme
cluster.

The implementation was changed to use Java/Android regex:

`Pattern.compile("\\X")`

for grapheme counting.

This is a real Unicode correctness fix, not a disabled test.

After the change, the full unit suite passed.

Relevant file:

`app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/SelectionMetrics.kt`

---

## 15. The original bug we still need to solve: stuck Transcribing

This is the main next product fix.

User-observed behavior:

Sometimes after a connection drop/provider failure the keyboard stays on `Transcribing…` with a
spinner for an extremely long time and does not return cleanly to an error/retry state.

Prior source investigation found:

### Batch HTTP

File:

`lib/dictate-core/.../OpenAiCompatibleClient.kt`

Provider config had a request timeout model around 120 seconds by default.

The request execution layer can retry retryable failures. Historically, 120 seconds multiplied across
several attempts could feel like a hang.

An upstream issue (#337) identified that large uploads could consume the full timeout then retry the
same upload repeatedly. Upstream `main` later introduced better configurable request timeout handling.

### Realtime

Realtime WebSocket intentionally uses no ordinary read/call timeout for the long-lived socket.

However, `DictateController.stopRealtimeAndFinalize()` already has a roughly 1.2-second finalization
watchdog before cancelling/falling back.

Therefore a truly endless visible `UiState.Transcribing` likely needs a **higher-level state-machine
watchdog**, not merely another OkHttp timeout.

### Recommended design for our fix

Do not simply put a hard 60-second cap on every valid transcription.

Instead:

1. when entering the visible Transcribing state, track the active transcription job/request;
2. track progress / last meaningful activity if possible;
3. enforce a configurable **stalled** timeout at the controller/state layer;
4. if the stall expires:
   - cancel the coroutine job;
   - cancel associated OkHttp call/WebSocket;
   - leave `UiState.Transcribing`;
   - produce a clear timeout/network error state;
   - retain the recorded audio;
   - offer Retry / Save audio / local fallback where appropriate;
5. prevent automatic retry loops from multiplying billable POSTs after ambiguous timeouts.

For billable/non-idempotent transcription POSTs, prefer fewer automatic ambiguous retries. The user
can explicitly resend preserved audio.

A brief network transition should not instantly destroy a valid request; if using
`ConnectivityManager`, debounce it or use it as a signal rather than the sole timeout mechanism.

---

## 16. Current upstream timeout improvement already observed

Upstream issue #337 (September 2026) was important:

The developer found that a large file upload could use a 120-second whole-call budget and then be
retried, causing approximately 4 uploads / ~8 minutes before final failure.

Upstream `main` added:

- longer special budget for large file import;
- separate no-byte/silence behavior;
- Settings → AI providers → Network → Request timeout;
- allowed request timeout roughly 30–600 seconds;
- default around 120 seconds.

Our fork began from an upstream `main` state that already contained the newer timeout architecture.

When implementing our stuck-Transcribing fix, inspect current code first and do not duplicate existing
timeout handling blindly.

---

## 17. Release APK — next immediate infrastructure task

Debug APK has been built successfully.

The next packaging task is to produce the first **release APK signed with the permanent Kapijuja
Voice key**.

Preferred safe workflow:

1. GitHub Actions may build an unsigned release artifact without receiving the private key;
2. materialize/download that artifact;
3. sign it using the user's permanent Kapijuja Voice JKS;
4. verify with `apksigner verify --print-certs`;
5. confirm package id is `net.kapijuja.voice`;
6. install it alongside official Dictate;
7. future releases must use the exact same key.

Alternative later: place signing material in GitHub Actions Secrets, but only if the user deliberately
wants cloud CI signing. Do not upload the JKS to the public repository.

---

## 18. First device smoke test after release signing

Test at minimum:

1. install official Dictate and Kapijuja Voice side by side;
2. Android keyboard enable/select flow;
3. ordinary typing;
4. suggestions/autocorrect;
5. microphone permission;
6. voice recording;
7. one known BYOK provider, ideally OpenAI/Groq;
8. Gemini/OpenRouter if configured;
9. custom OpenAI-compatible endpoint;
10. local/on-device model if available;
11. history/retry;
12. floating button;
13. screen-off/background behavior where applicable;
14. Wear companion only after phone baseline is stable.

Specifically confirm no UI mentions buying Dictate credit and no request reaches
`api.dictatekeyboard.com`.

---

## 19. Future upstream synchronization strategy

We imported upstream history, rather than starting from a ZIP snapshot, so future comparison is
possible.

When syncing future Dictate changes:

1. fetch/inspect upstream changes;
2. do NOT blindly merge;
3. specifically inspect whether upstream reintroduces:
   - Dictate Cloud;
   - Billing;
   - Cloud UI/state;
   - upstream store/funding links;
4. preserve our package id, branding and signing configuration;
5. run the Cloud/Billing audit;
6. run full CI/tests;
7. manually review provider and model catalog changes.

Because Cloud was deeply integrated upstream, automatic merges may compile while partially restoring
its assumptions. Treat Cloud-related conflicts as intentional fork boundaries.

---

## 20. Things NOT to do

### Do not regenerate the release signing key

Use the saved permanent Kapijuja Voice key.

### Do not change application id casually

Keep `net.kapijuja.voice`.

### Do not restore Dictate Cloud/Billing during upstream sync

Kapijuja currently has no prepaid-minute backend.

### Do not blindly rename every old `net.devemperor.dictate` or Dictate identifier

Some old identifiers can exist for:

- migration compatibility;
- asset/catalog naming;
- historical preferences;
- upstream file IDs.

Change only user-facing/project-identity references when safe. Internal compatibility IDs need code
analysis first.

### Do not copy giant native runtime binaries into Git

Keep using the verified fetch script.

### Do not remove Apache/FlorisBoard/Dictate attribution

The project is open source and derivative attribution must remain.

### Do not “fix” stuck transcription only by increasing network timeout

The complaint is an endless visible state; solve the controller/state lifecycle.

---

## 21. Suggested next-chat execution order

When the next conversation starts, do this:

### Stage A — verify repository state

- inspect current `main`;
- inspect latest successful GitHub Actions run;
- confirm package id and current version;
- read this file and README.

### Stage B — signed release build

- build/download unsigned release;
- obtain the existing Kapijuja signing backup from the user;
- sign;
- verify signature/package;
- provide installable APK.

### Stage C — device smoke-test fixes

React to any install/runtime issues from the user's real Android device.

### Stage D — stuck Transcribing fix

Inspect current:

- `DictateController.kt`
- `OpenAiCompatibleClient.kt`
- realtime client/session code
- UiState transitions
- retry/cancellation code

Implement a state-level stalled-transcription watchdog with preserved audio and explicit retry.

### Stage E — test failure modes

Test:

- Wi-Fi/mobile switch;
- airplane mode during request;
- DNS/connect failure;
- provider 5xx;
- provider timeout;
- invalid API key;
- quota error;
- user cancel;
- long recording;
- local fallback.

### Stage F — release

Increment versionCode/versionName, build with the same permanent signing key, verify and publish the
artifact/release if requested.

---

## 22. User development preference relevant to this project

The user wants practical comfort and reliability rather than artificial restrictions.

For this project that means:

- keep provider choice open;
- do not force one AI vendor;
- make failure states recoverable;
- preserve audio when transcription fails;
- make retries explicit where billing ambiguity exists;
- avoid UI states that spin forever;
- prefer a simple keyboard workflow over unnecessary configuration barriers.

The keyboard is already considered very good; changes should therefore be targeted rather than a
large redesign.

---

## 23. End state at handoff

At this handoff:

- source has been forked into the user's public repository;
- upstream Git history is preserved;
- Dictate Cloud backend and Android client/payment path were removed;
- Google Play Billing was removed;
- application identity was changed to Kapijuja Voice / `net.kapijuja.voice`;
- a separate gold-K app icon was created;
- project links/docs were rebased to Kapijuja;
- a permanent release signing key was created and backed up outside Git;
- CI was added;
- Cloud regression audit passes;
- native STT fetch passes;
- APK build passes;
- unit tests pass;
- Unicode grapheme counting was fixed;
- verified bootstrap was fast-forwarded to `main`;
- the next major functional change is the stuck-`Transcribing…` watchdog;
- the next packaging change is the first properly signed Kapijuja Voice release APK.

**Start the next chat from this file. Do not rediscover the bootstrap from scratch.**


---

## 21. Kapijuja recovery UX phase — 2026-09-09

The project owner explicitly wants captured speech to remain recoverable when a provider hangs or fails.

Work now in progress / committed on `main`:

- explicit Stop button in the Smartbar while `Transcribing…`;
- Stop cancels the in-flight provider job but keeps a private resend copy instead of deleting the audio;
- the resend state offers Send again and explicit discard;
- stopped-audio rescue preserves the original container extension and is safe even when Stop is pressed
  during a resend of an already-retained file;
- source-audio history retention defaults ON for new Kapijuja installs;
- existing retention limits remain 50 entries / 30 days / 200 MB;
- History is promoted beside Clipboard in the default Smartbar action order;
- README and PROJECT_HANDOFF describe the new recovery model.

Important existing architecture discovered during this work:

- `DictateHistoryStore` already stores retained audio in `filesDir/dictate_history/`;
- history entries already support playback/export/pinning and re-transcription;
- `DictateController.retranscribeHistoryEntry()` replays a saved recording through the CURRENT active
  transcription provider. Therefore cross-provider re-recognition already works if the user changes the
  active provider first.

Next planned product step:

Add an explicit provider chooser directly on a retained history recording. The user should be able to
select OpenAI/Groq/Gemini/custom/on-device for that one replay without changing the global default first.
The chooser should live close to the in-keyboard History panel, not several settings screens away.

After that, implement a provider-independent no-progress watchdog. Its terminal action should reuse the
same preserved-audio resend state created by the manual Stop path.
