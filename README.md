# Kapijuja Voice

**Kapijuja Voice** is an open-source Android voice keyboard derived from
[Dictate Keyboard](https://github.com/DevEmperor/DictateKeyboard), which itself is built on
[FlorisBoard](https://github.com/florisboard/florisboard).

The project keeps the full keyboard, bring-your-own-key AI providers, self-hosted endpoints,
real-time transcription, offline/on-device speech recognition, Wear OS support, and the other
open-source keyboard features.

## Project identity

- Android application id: `net.kapijuja.voice`
- The upstream Dictate app and Kapijuja Voice can be installed side by side.
- Kapijuja Voice uses its own release signing key.
- Dictate Cloud and its Google Play Billing purchase flow are intentionally not part of this fork.

## AI providers

Kapijuja Voice talks directly to the provider selected by the user, including OpenAI, Gemini, Groq,
OpenRouter, Mistral, Deepgram, Soniox, ElevenLabs, AssemblyAI, and compatible custom servers.
Provider credentials are stored on the device and used only for the configured endpoint.

On-device transcription is supported through sherpa-onnx and ONNX Runtime. Large speech models are
downloaded on demand and are not stored in this Git repository.

## External build artifacts

The repository intentionally does not commit sherpa-onnx and ONNX Runtime native binaries. Run:

```bash
./tools/fetch-sherpa-onnx.sh
```

The script downloads pinned artifacts and verifies their SHA-256 checksums.

For now, downloadable speech-model and keyboard-language assets continue to use the original Dictate
GitHub release mirrors with fixed hashes. These are static release assets, not the Dictate Cloud
transcription/payment service.

## Building

Tool versions are pinned in `gradle/tools.versions.toml`. The current build uses JDK 17 and the
Android SDK/NDK versions declared by the project.

```bash
./tools/fetch-sherpa-onnx.sh
./gradlew :app:assembleDebug
```

Release signing is configured through an untracked `keystore.properties`. Never commit a signing
key or its passwords.

## License and attribution

Kapijuja Voice remains distributed under the Apache License 2.0. The original `LICENSE` and required
`NOTICE` attribution are retained. See `NOTICE` for Dictate Keyboard and FlorisBoard attribution.

The installed product is branded **Kapijuja Voice** while its origin as a Dictate Keyboard fork is
stated explicitly here.
