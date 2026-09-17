# AI provider toggles, automatic rewording, and learned suggestions

This is the current handoff for the next development chat. Read this file before touching provider/rewording or suggestion-learning code.

## Status

The requests in this document are **active work, not yet implemented**. Do not assume that the switches or Smartbar fixes described below already exist just because they are documented here.

Repository: `standartkiev-pixel/DictateKeyboard-kapijuja`

The user currently prefers `gpt-4o-mini-transcribe` over `gpt-transcribe` because the larger/newer transcription option felt slower in everyday keyboard use. That is a user model choice, not a required code default change.

## 1. Provider editor: put behavior switches next to the model fields

The built-in provider editor currently shows, for OpenAI and compatible providers:

- API key;
- Transcription model;
- Real-time model;
- Rewording model;
- Single-call multimodal.

There is no obvious `Off` / `None` next to the Real-time or Rewording model fields. This is confusing because the user sees a model selected and cannot tell whether that model is merely configured or actively used.

### Required UX

Add explicit switches in the same provider editor dialog:

- **Real-time transcription** — ON/OFF;
- **Automatic rewording** — ON/OFF.

Keep the chosen model id when a switch is OFF. The model field may be disabled/greyed while OFF, but its saved value must not be erased. Turning the switch back on should restore the previous configuration immediately.

`Single-call multimodal` remains a separate feature. Do not repurpose it as a realtime or rewording switch.

### Rewording behavior that must be preserved

The existing `prefs.dictate.rewordingEnabled` is a master capability/UI switch. When it is disabled, the manual rewording/prompt/magic-wand functionality disappears as well. The user relies on the magic wand for manual rewriting and translation, so this is **not** the switch requested here.

The new Automatic rewording switch must control only automatic post-processing after dictation. With Automatic rewording OFF:

- raw STT text should be committed without automatic GPT/rewording calls;
- manual magic-wand rewording must still work;
- manual translation prompts must still work;
- saved prompt UI must remain available;
- turning Automatic rewording ON later must restore the automatic chain.

Current code facts to preserve/adjust:

- `DictateRewordingScreen.kt` exposes `prefs.dictate.rewordingEnabled` as the master rewording switch.
- `DictateController.postProcessTranscript()` currently returns raw text immediately when `rewordingEnabled` is false, then otherwise runs automatic formatting and auto-apply prompts.
- `DictateController.rewordingWillFollow()` currently checks the master switch plus `autoFormattingEnabled` or any `autoApply` prompt.
- the single-call multimodal prompt builder similarly folds automatic formatting/auto-apply prompts into the request under the master rewording gate.

A clean implementation should introduce a separate persistent preference such as `automaticRewordingEnabled` and gate **automatic** paths with it, while keeping manual prompt/rewording entry points governed by the existing master capability.

Do not accidentally make `autoFormattingEnabled` or each prompt's `autoApply` setting meaningless. The intended hierarchy is:

1. rewording feature available (`rewordingEnabled`);
2. automatic post-processing allowed (`automaticRewordingEnabled`);
3. within that automatic chain, run only enabled automatic features (`autoFormattingEnabled`, prompts marked `autoApply`).

### Realtime behavior

Before adding the realtime switch, trace the exact realtime session-start condition in `DictateController` and provider capability code. Do not implement a cosmetic switch that merely hides the field.

With Real-time transcription OFF:

- no realtime WebSocket/session/API path should start;
- ordinary batch transcription must continue to work normally;
- the selected realtime model must remain stored for later reuse;
- fallback/recovery behavior must remain intact.

With it ON, current realtime behavior should remain unchanged unless a separate bug is found.

### Primary files

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictate/DictateProvidersScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictate/DictateRewordingScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt` — only realtime/rewording sections
- provider account/preset classes used by `ProviderEditorDialog`
- relevant string resources and preference tests

## 2. Suggestions and personal vocabulary

The user wants the keyboard to learn deliberate repeated typing so common personal words and email addresses become useful suggestions. A frequently typed email should rise toward the top when its prefix is typed.

The user also reported inconsistent candidate-strip visibility:

- placing the cursor inside a word before typing may leave the upper Smartbar/menu unchanged and show no word candidates;
- after typing begins, candidates appear;
- after the keyboard has entered that typing/candidate state, moving the cursor elsewhere can make candidates appear again;
- sometimes another Smartbar surface appears to cover or replace the candidate strip.

Investigate state transitions, not just candidate ranking. Suggestions should not silently disappear because of stale Smartbar mode/session state.

### Important: automatic learning already exists

Do **not** build a second learning database. Current code already contains a substantial personal-learning implementation:

`ime/dictionary/LearnedWords.kt`

- Room database `dictate_learned_words`;
- learned words with count, last-used time, language and promotion state;
- learned bigrams for personal next-word prediction;
- prefix lookup ordered by learned score;
- address-like strings are intentionally supported.

`ime/nlp/latin/WordLearningGate.kt`

- first accepted sighting: remembered, not shown;
- second sighting: may appear in suggestions;
- third sighting: promoted to personal dictionary;
- rejecting/taking back an autocorrection has extra learning weight;
- unpromoted entries decay slowly (60-day half-life);
- likely slips are filtered using tap evidence and dictionary-neighbour evidence;
- email/address-like forms can be learned;
- private/incognito/password/no-suggestion contexts and non-typed origins are excluded.

`AppPrefs.kt` already has:

- `suggestion__learn_typed_words` / `learnTypedWords`;
- current default: `false`.

So the next chat must first locate the existing UI for `learnTypedWords` and trace where typed words are recorded and where learned candidates are merged/ranked. Fix or expose the existing system instead of duplicating it.

### Desired behavior

- repeated deliberate words and email-like strings become candidates;
- a frequently used matching personal candidate should outrank weaker generic candidates when appropriate;
- learning must never run in password/private/incognito fields;
- obvious typos must not be aggressively promoted merely because they were typed once;
- manual personal-dictionary control should remain possible.

The user likes Gboard's idea of offering **Add to dictionary** for an unknown word that the keyboard tried to correct. Before adding a new popup, inspect existing candidate long-press, personal dictionary, undo-autocorrect, and learned-word affordances. Prefer one clear, non-intrusive path over another modal on every unknown word.

A reasonable UX is to make an explicit Add-to-dictionary action available when an unknown word is being challenged/corrected, while automatic learning continues quietly for repeated deliberate words. Do not interrupt normal typing for every unfamiliar token.

### Candidate-strip investigation

Trace:

- Smartbar candidate display mode and competing Smartbar surfaces;
- cursor/selection update handling;
- whether candidate recomputation is triggered on cursor moves before the first keypress;
- composing-word ownership before and after typing starts;
- whether candidate state is deliberately suppressed when no active composing word exists;
- whether stale UI state survives moving the cursor after a typed word;
- email/address tokenization and prefix matching.

Do not force suggestions over every Smartbar state if another surface is intentionally higher priority (error, resend, dictation status, confirmation, etc.). The fix should distinguish intentional temporary overlays from a candidate strip that was simply never refreshed.

### Primary files

Start with these areas and expand only as dependencies require:

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/LearnedWords.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/WordLearningGate.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt` — suggestion-learning prefs only
- Smartbar/candidate composables and candidate display mode
- Latin suggestion provider/ranking code that merges learned candidates
- cursor/selection/composing-state controller code
- existing personal dictionary UI/action code

## Safety and regression checks

Provider/rewording:

- Automatic rewording OFF: one dictation produces STT text without an automatic chat/reword request.
- Manual magic wand still rewrites/translates while Automatic rewording is OFF.
- Automatic rewording ON: existing auto-format + auto-apply chain still works.
- Realtime OFF: no realtime session/network startup; batch STT still works.
- Realtime ON: current realtime flow still works.
- Toggle OFF/ON must not erase the selected model id.
- Single-call multimodal must obey the same automatic-rewording semantics.

Learning/suggestions:

- second accepted sighting becomes suggestible;
- third accepted sighting promotes as designed;
- repeated email/address-like value is learnable and can rank by frequency;
- private/password/incognito fields do not learn;
- likely slip/typo gating still works;
- learned-candidate frequency can affect ordering without making learned items unsafe auto-corrections too early;
- cursor movement before and after typing refreshes candidate UI consistently where suggestions are applicable;
- dictation/error/resend overlays still retain intentional priority over candidates.

Run focused unit tests, `scripts/architecture-audit.sh`, phone tests and Wear tests/CI required by the repository. Keep behavior changes small and separable. Do not use this task as an excuse for a wholesale Smartbar or `DictateController` rewrite.

## Handoff rule

When this work is implemented, replace the open-work statements in this file with the resulting invariants and commit/PR references. Do not append a chronological diary. Git history is the archive.
