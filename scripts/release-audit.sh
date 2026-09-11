#!/usr/bin/env bash
set -euo pipefail

fail_if_tracked() {
  local pattern="$1"
  shift
  if git grep -nE "$pattern" -- "$@"; then
    echo "Release audit rejected tracked content matching: $pattern" >&2
    exit 1
  fi
}

# Product identity and the minimal release line must stay coherent.
git grep -q 'applicationId = "net.kapijuja.dictate"' -- app/build.gradle.kts
git grep -q 'rootProject.name = "DictateKapijuja"' -- settings.gradle.kts
git grep -q '^projectVersionCode=5$' -- gradle.properties
git grep -q '^projectVersionName=0.1.0$' -- gradle.properties

# No production credentials, private keys, old Cloud/Billing wiring, or accidentally committed signing material.
fail_if_tracked '(sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|gh[pousr]_[A-Za-z0-9]{30,}|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY)' \
  ':!keystore.properties.template'
fail_if_tracked '(api\.dictatekeyboard\.com|com\.android\.vending\.BILLING|billing-ktx|DictateCloud|dictate__cloud_)' \
  ':!README.md' ':!PRIVACY_POLICY.md' ':!SECURITY.md' ':!PROJECT_HANDOFF.md' ':!NEXT_CHAT.md' \
  ':!FULL_PROJECT_HANDOFF_2026-09-09.txt' ':!KAPIJUJA_VOICE_PROJECT_HANDOFF_2026-09-09.txt' \
  ':!DEVELOPER_HANDOFF.md' ':!DEVELOPER_GUIDE.md' ':!NOTICE' ':!.github/workflows/**' \
  ':!scripts/release-audit.sh'

if git ls-files | grep -Eq '(^|/)(keystore\.properties|.*\.(jks|keystore))$'; then
  echo "Release audit rejected tracked signing material." >&2
  exit 1
fi

# Prereleases intentionally share one public test-only key so clean CI runners produce upgrade-compatible
# APKs. Its decoded checksum is pinned here; production signing material remains forbidden above.
test "$(base64 --decode .github/test-signing/kapijuja-prerelease-debug.keystore.b64 | sha256sum | cut -d' ' -f1)" = \
  "100a4ef0a4d5d2c702e5b9828f6fd71acb051fd61fc85c98ba14379cfd4700d2"

# Regression tripwires for the resource-safety failures fixed before RC2.
fail_if_tracked 'LinkedBlockingQueue<Event>\(\)' \
  app/src/main/kotlin/dev/patrickgold/florisboard/dictate/audio
fail_if_tracked 'thread\??\.join\(\)' \
  app/src/main/kotlin/dev/patrickgold/florisboard/dictate/audio/RecordingController.kt \
  wear/src/main/kotlin/net/devemperor/dictate/wear/audio/WearAudioRecorder.kt
fail_if_tracked 'MediaPlayer\(\)\.apply' \
  app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictate/AudioPlayback.kt

echo "Release source audit passed."
