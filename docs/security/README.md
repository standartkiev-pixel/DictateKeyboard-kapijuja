# Kapijuja signing-key recovery archive (AES-256-GCM)

**Recovered on 2026-11-11 from ChatGPT Library files originally saved 2026-09-08.** This directory stores **encrypted ciphertext only**, never a raw JKS, keystore password, recovery encryption key or `keystore.properties`.

Encrypted backup: [kapijuja-signing-recovery-2026-09-08.aes256gcm.b64](kapijuja-signing-recovery-2026-09-08.aes256gcm.b64)

## Two different original keys (do not mix them)

1. **Standalone legacy keystore**, originally `kapijuja-voice-release.jks` (3797 bytes): alias `kapijuja-voice`, keystore SHA-256 `708b2c4202e7d7cff995f69ab6fda4f9bd69cad2cc146dea412da9665e13feaf`, **certificate SHA-256** `58A177EE9D9CDAFAE8F45967B713C13C00F8FA2E437939A54E5E40B2B0493C6B`.
2. **Another distinct JKS inside the original backup ZIP**, alias `kapijuja_voice_release`, keystore SHA-256 `1db16eb1226b6e9d4c20719780765ed0bdeb088ba68714e6069022661e99a81b`, **certificate SHA-256** `B7000716F77AFD04388D71546B128E9A934D8B7934E93A094E7FA2DDEE7C296`.

These are not interchangeable. Both JKS files have been read successfully using their corresponding recovery records. It is **not established** which, if either, signed an already-distributed stable `net.kapijuja.dictate` APK. The prerelease `net.kapijuja.dictate.debug` has a **third, separate** public test signing identity in `.github/test-signing`.

The ZIP *inside* the encrypted backup contains sensitive passwords and is **not itself encrypted**. **Do not publish the decrypted ZIP or either JKS.**

The outer recovered backup was packed into a ZIP and encrypted with a unique randomly generated 256-bit AES-GCM key. The recovery key was delivered **privately to the project owner**, not placed in this repository.

## Restore

The recovered file is an ASCII base64 encoding of binary `header || 12-byte nonce || AES-256-GCM ciphertext+tag`. Header bytes in UTF-8: `KAPIJUJA-SIGNING-RECOVERY-v1\0`; the same header is used as authenticated additional data.

On a trusted machine with Python and `cryptography`:

```bash
python -m pip install cryptography
export KAPIJUJA_RECOVERY_KEY_B64='PASTE_PRIVATE_RECOVERY_KEY_HERE'
python - <<'PY'
import os, base64, pathlib, zipfile, io
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

src = pathlib.Path("kapijuja-signing-recovery-2026-09-08.aes256gcm.b64")
raw = base64.b64decode(src.read_text().strip(), validate=True)
header = b"KAPIJUJA-SIGNING-RECOVERY-v1\x00"
assert raw.startswith(header), "Unexpected archive header"
nonce = raw[len(header):len(header)+12]
ciphertext = raw[len(header)+12:]
key = base64.b64decode(os.environ["KAPIJUJA_RECOVERY_KEY_B64"], validate=True)
assert len(key) == 32
plain_zip = AESGCM(key).decrypt(nonce, ciphertext, header)
dest = pathlib.Path("kapijuja-signing-private")
dest.mkdir(mode=0o700, exist_ok=True)
with zipfile.ZipFile(io.BytesIO(plain_zip)) as archive:
    archive.extractall(dest)
print("Restored into:", dest)
PY
unset KAPIJUJA_RECOVERY_KEY_B64
```

The extraction produces a `MANIFEST.txt`, the legacy JKS/credentials under `legacy/`, and the original second backup under `archive/`. Extract the second ZIP **only into private storage** if required. Verify JKS and certificate fingerprints with `keytool -list -v -keystore <file>` and compare the certificate SHA-256 to any existing signed stable APK before selecting a key.

**Do not automatically connect this encrypted archive to release builds.** Configure the selected actual signing key on trusted CI using GitHub Actions Secrets and a restricted deploy procedure, not checked-in plaintext; update `keystore.properties` locally without committing it.

Do not add the recovery key to repository secrets scanners, git history, CI logs, Issues, pull requests or source code.
