# Kapijuja Voice

**Kapijuja Voice** is an open-source Android voice keyboard derived from
[Dictate Keyboard](https://github.com/DevEmperor/DictateKeyboard), which itself is built on
[FlorisBoard](https://github.com/florisboard/florisboard).

The goal of this fork is to keep the excellent full keyboard and voice/AI architecture while making
the application independent from the upstream Dictate Cloud payment service and giving the fork its
own Android identity, signing key and release line.

For the complete development handoff and current project state, read **[NEXT_CHAT.md](NEXT_CHAT.md)**.

## Current status

The fork is functional and builds successfully in GitHub Actions.

- Product name: **Kapijuja Voice**
- Full name: **Kapijuja Voice Keyboard**
- Android application id: `net.kapijuja.voice`
- Current fork version line: `0.1.0` / versionCode `1`
- Main development branch: `main`
- Bootstrap/history branch retained: `kapijuja-bootstrap`
- The official Dictate app and Kapijuja Voice can be installed side by side.
- Kapijuja Voice has its own permanent release signing identity.
- Dictate Cloud and Google Play Billing are intentionally removed.
- BYOK online providers, custom/self-hosted endpoints and on-device STT remain available.
- CI currently builds a debug APK, uploads it as an artifact and runs the unit test suite.

## What was removed from upstream Dictate

Kapijuja Voice does **not** include the upstream prepaid Dictate Cloud service.

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

Kapijuja Voice talks directly to the provider selected by the user. The current architecture supports
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

Kapijuja Voice has its own permanent release key.

- Package: `net.kapijuja.voice`
- Key alias: `kapijuja_voice_release`
- Key type: RSA-4096
- The actual keystore and passwords are **not stored in Git**.

The repository contains only:

```
keystore.properties.template
```

For local release signing, create an untracked `keystore.properties` and point it to the permanent
Kapijuja Voice keystore.

**Never generate a new release key for normal future releases.** Android in-place updates require the
same signing identity.

The repository already ignores:

```
*.jks
*.keystore
keystore.properties
```

## Branding

Kapijuja Voice is intentionally visually distinct from Dictate.

- App name: Kapijuja Voice
- Adaptive launcher icon: dark background with a gold **K**
- Project/About/Issues/Privacy/Changelog links point to this repository.
- Upstream attribution is preserved in `NOTICE` and source copyright headers.

The fork remains Apache-2.0.

## Unicode fix made during the fork

The upstream `SelectionMetrics` test exposed inconsistent JDK 17 handling of ZWJ emoji via
`java.text.BreakIterator`.

Character/grapheme counting was changed to Java/Android regex `\\X`, which correctly treats extended
grapheme clusters such as family emoji as one visual character.

All unit tests passed after this change.

## Immediate roadmap

The next development work should proceed roughly in this order:

1. produce the first **release** APK signed with the permanent Kapijuja Voice key;
2. verify side-by-side installation with official Dictate;
3. test basic typing, microphone dictation, OpenAI/Groq/Gemini/custom provider paths and local STT;
4. fix the original user-facing problem that started this fork: transcription can sometimes remain
   indefinitely in the `Transcribing…` state after a network/provider failure;
5. add a state-level transcription watchdog while preserving the recorded audio for retry;
6. consider reducing or changing automatic retries for billable/non-idempotent transcription POSTs;
7. later decide whether to mirror upstream model/dictionary release assets under Kapijuja.

See **[NEXT_CHAT.md](NEXT_CHAT.md)** before modifying the project.
