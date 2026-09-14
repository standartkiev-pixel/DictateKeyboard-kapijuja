# Transcription reliability context

Use this file only for stuck Transcribing, cancellation, resend, recovery and network-fault work.

## Current invariants

- Visible batch transcription has a controller-level no-progress watchdog.
- Provider/network progress refreshes its heartbeat.
- Stop/stall cancels the owned request and preserves recoverable audio when privacy/settings allow it.
- Resend is explicit; do not silently multiply ambiguous billable transcription POSTs.
- Request/cache files are request-owned and uniquely named; never return to one shared temp filename.
- Realtime has its own short finalization watchdog and can fall back to batch.
- Long-form owns its segment audio through terminal cleanup and can construct rescue audio.
- Non-sensitive Stop/watchdog recovery may persist through process death; sensitive fields must not use that staging.

## Scope rule

For a concrete failure, inspect:

1. the relevant lifecycle section of `DictateController.kt`;
2. the exact provider/client selected by the user;
3. the corresponding test(s);
4. the device log window around that request.

Do not read every provider implementation or the complete historical handoffs by default.

## Faults worth testing on device

Network loss mid-upload, Wi-Fi/mobile switch, DNS/connect failure, provider 5xx, invalid key, quota/rate limit, user Stop, process death immediately after Stop/stall, long-form rescue, local/native cancellation and realtime fallback.

Automated tests complement these scenarios but do not replace real Android lifecycle/network tests.
