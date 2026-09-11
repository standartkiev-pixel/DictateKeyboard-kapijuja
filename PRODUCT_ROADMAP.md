# Dictate Kapijuja — Product Review and Roadmap

Updated: 2026-09-11

## Product direction

Kapijuja should remain a serious voice-first keyboard for work, accessibility and long-form input.
Reliability, privacy and recoverability come before decorative themes, stickers or engagement features.

The phone application is the primary keyboard. The Wear OS module is a real independent IME added later:
it records on the watch, prefers sending audio to a reachable phone for transcription, and can fall back
to a direct provider call when standalone mode and watch connectivity are available. The project was not
created only for watches, but the watch path is one of its strongest differentiators.

## Verified baseline

- Provider-independent no-progress timeout with a visible shrinking budget and explicit Stop.
- Retained-audio recovery after cancellation, timeout and selected process-death paths.
- Actual Android recording-route indicator for phone, Bluetooth, wired and USB inputs.
- Offline/on-device providers and an automatic local fallback path.
- Phone-tethered and standalone Wear OS dictation.
- User-selected providers and user-owned API credentials; upstream Dictate Cloud billing is removed.
- Quiet typing and the requested clipboard defaults for clean installations.

## Review findings

1. The recovery design is substantially safer than deleting audio when a network call is cancelled. Its
   remaining risk is runtime/OEM behavior: very large rescue copies, AudioRecord drivers that block, rapid
   Stop-to-new-recording races, and process death must continue to be tested on physical devices.
2. The countdown is deliberately a no-progress budget, not fake provider completion. Blue, amber and red
   urgency states now make that distinction visible while seconds and accessibility semantics keep color
   from being the only signal.
3. The Wear transport already retries result delivery and returns structured failures. The next useful
   improvement is durable, idempotent delivery rather than adding more watch-side decoration.
4. Provider choice is powerful but difficult to diagnose. A privacy-safe local request journal would reduce
   uncertainty without storing transcript contents or API keys.
5. Professional users benefit more from explicit commands, domain terminology and hardware-key support than
   from automatic rewriting that can silently alter meaning.

## Priority 0 — prove reliability

1. Add an opt-in diagnostic export containing timestamps, provider kind, request phase, retry count, network
   state, audio route, duration and sanitized exception category. Never include audio, transcript, prompts or
   credentials unless the user explicitly selects them.
2. Run a repeatable physical-device matrix: network loss during upload/polling, Stop then immediate record,
   screen lock, keyboard hide/reopen, Bluetooth disconnect, wired/USB routing, process death, long recordings,
   low storage, low memory and RC upgrade installation.
3. Add memory regression measurements for repeated record/transcribe/cancel cycles. Track retained jobs,
   OkHttp calls, file descriptors, AudioRecord instances and private rescue files after every cycle.
4. Make every watch request carry an idempotent request ID and persist an acknowledgement until the watch
   confirms delivery. This prevents duplicate provider billing and lost text across reconnects.

## Priority 1 — professional input

1. **Terminology profiles.** Import/export plain-text word lists and short context prompts for transport,
   engineering, legal, medical or personal names. Profiles must be chosen explicitly per dictation and must
   work with both cloud and compatible local recognizers.
2. **Explicit voice command mode.** Commands such as new paragraph, literal punctuation, undo last insertion,
   translate selection and formalize selection should require a visible command mode or confirmation. Ordinary
   dictation must never guess whether spoken words are commands.
3. **Reusable snippets and forms.** Offline text expansions with named placeholders for date, time, clipboard
   and cursor position. No automatic cloud processing is needed.
4. **Professional key layer.** Optional Ctrl, Alt, Tab, Esc, arrows, Home/End and function keys for terminals,
   remote desktops and engineering tools.
5. **Provider health view.** Local statistics for median latency, timeout/error rate, fallback count and an
   optional user-entered price estimate. This should expose reliability without sending telemetry.
6. **Per-app profiles.** Select language, provider, terminology profile, formatting prompt and privacy policy
   by target package, with a clear indicator and a global fallback.

## Priority 2 — phone and watch continuity

1. Add a watch-to-phone note inbox: dictate on the watch, receive confirmed text on the phone, then copy or
   insert it deliberately. Queue encrypted payloads locally while disconnected and expire them automatically.
2. Add headset-button push-to-talk with visible/haptic start, stop and failure acknowledgement. Never start an
   invisible background recording.
3. Allow the watch to display the same no-progress budget and transport path: phone, standalone or offline.
4. Add a one-tap handoff from a failed watch request to the phone's retained-audio retry screen.

## Evidence from active keyboard projects

The recurring serious requests are multilingual input, streaming voice recognition, password-manager access,
power-user keys, custom layouts, memory stability and voice-only operation. Examples:

- FUTO Keyboard: multilingual typing
  <https://github.com/futo-org/android-keyboard/issues/683>, password-manager action
  <https://github.com/futo-org/android-keyboard/issues/325>, power-user keys
  <https://github.com/futo-org/android-keyboard/issues/25>, streaming voice recognition
  <https://github.com/futo-org/android-keyboard/issues/130>.
- FlorisBoard: OOM stability <https://github.com/florisboard/florisboard/issues/677>, speech-to-text
  <https://github.com/florisboard/florisboard/issues/195>, special keys
  <https://github.com/florisboard/florisboard/issues/229>, custom layouts
  <https://github.com/florisboard/florisboard/issues/196>.
- Dictate: chained post-processing <https://github.com/DevEmperor/DictateKeyboard/issues/37>, custom-server
  reliability <https://github.com/DevEmperor/DictateKeyboard/issues/39>, voice-only accessibility entry
  <https://github.com/DevEmperor/DictateKeyboard/issues/91>, and microphone lifetime
  <https://github.com/DevEmperor/DictateKeyboard/issues/147>.

These reports are signals, not a vote count. The roadmap filters them through Kapijuja's reliability-first
scope and avoids features that would silently transform text or weaken privacy.

## Upstream billing reference

The original project uses Google Play Billing 8 for four consumable one-time credit packs. Google Play owns
the checkout and returns a purchase token; the application does not integrate Stripe, Paddle or a direct card
processor. The server verifies that token with the Google Play Developer API, grants credit, acknowledges the
purchase and consumes it only after credit is safely recorded. Google Real-time Developer Notifications report
refunds. Cloudflare Workers, D1 and Durable Objects implement the server ledger and metering, but Cloudflare is
infrastructure, not the customer payment method. PayPal appears only as a separate donation link.

Primary source:

- <https://github.com/DevEmperor/DictateKeyboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/dictate/cloud/DictateCloudBilling.kt>
- <https://github.com/DevEmperor/DictateKeyboard/blob/main/cloud/README.md>
- <https://github.com/DevEmperor/DictateKeyboard/blob/main/cloud/src/google.ts>

Kapijuja must not reuse the upstream package products or service account. Any future paid version needs its
own Play Console application, product IDs, legal/tax configuration and independently operated backend.
