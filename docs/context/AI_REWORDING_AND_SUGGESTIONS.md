# AI provider toggles, automatic rewording, and learned suggestions

Read this file before changing provider/rewording or suggestion-learning code.

## Current status

Repository: `standartkiev-pixel/DictateKeyboard-kapijuja`

Provider runtime switches are implemented and merged on `main` in PR #2, merge commit `2844aaa13e54d5263331da551921a23c1312fe37`.

The candidate-strip refresh race is being fixed separately in PR #3, branch `kapijuja/suggestion-strip-consistency`. Until PR #3 is merged, treat that part as pending device/CI validation rather than established `main` behavior.

The user currently prefers `gpt-4o-mini-transcribe` over `gpt-transcribe` for everyday keyboard use because it felt faster. That is a user model choice, not a required code default.

## 1. Provider editor invariants — implemented

The provider editor now exposes explicit runtime controls beside the model configuration:

- **Real-time transcription** — ON/OFF;
- **Automatic rewording** — ON/OFF.

Turning either switch OFF preserves the selected model id. The switch controls runtime behavior, not whether a model remains configured.

`Single-call multimodal` remains a separate feature.

### Rewording hierarchy

Do not collapse these three levels into one switch:

1. `rewordingEnabled` — master capability/UI switch for rewording and the manual magic-wand/prompt tools;
2. `automaticRewordingEnabled` — permission for automatic post-processing after dictation;
3. inside the automatic chain, `autoFormattingEnabled` and prompts marked `autoApply` decide what actually runs.

With Automatic rewording OFF:

- batch/normal STT text is committed without automatic formatting or auto-apply prompt calls;
- manual magic-wand rewriting still works;
- manual translation prompts still work;
- saved prompt UI remains available;
- Single-call multimodal does not fold automatic rewording instructions into its request.

Do not replace `automaticRewordingEnabled` with the master `rewordingEnabled`: disabling the master intentionally removes the manual tools too.

### Realtime invariant

The existing global `realtimeTranscription` runtime preference is exposed in the provider editor rather than inventing a second realtime state.

With Real-time transcription OFF:

- the normal controller gates prevent realtime session/WebSocket startup;
- ordinary batch transcription remains available;
- the selected realtime model remains stored for later reuse.

With it ON, the existing realtime path is unchanged.

### Provider implementation files

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictate/DictateProvidersScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt` — automatic/realtime gates only
- `app/src/main/res/values/strings.xml`

PR #2 passed the repository Android CI, architecture audit, phone/Wear builds and unit tests before merge.

## 2. Suggestions and personal vocabulary

Do **not** build another learning database. The repository already has the personal-learning system.

`ime/dictionary/LearnedWords.kt` provides:

- Room database `dictate_learned_words`;
- learned words with count, recency, language and promotion state;
- learned bigrams for personal next-word prediction;
- prefix lookup ordered by learned score;
- support for address-like strings.

`ime/nlp/latin/WordLearningGate.kt` provides the learning gate:

- first accepted sighting: remembered but not suggested;
- second sighting: may become suggestible;
- third sighting: eligible for promotion to the personal dictionary;
- likely slips are filtered using tap/dictionary evidence;
- unpromoted entries decay over time;
- private/incognito/password and non-typed origins are excluded.

`AppPrefs.kt` already contains `suggestion__learn_typed_words` / `learnTypedWords`; it remains opt-in by default. Do not change that privacy default casually.

### Existing explicit dictionary controls

Do not add a duplicate Add-to-dictionary system without a new UX reason.

Current UI already has:

- long-press a normal word candidate in `CandidatesRow.kt` → add it to the personal dictionary;
- long-press a learned candidate → forget it;
- `LearnedWordsScreen.kt` → **Add now** to promote a learned word manually;
- dictionary settings for managing the internal personal dictionary and learned vocabulary.

These paths use the same existing dictionary/learning plumbing and should remain the source of truth.

## 3. Candidate-strip consistency — PR #3

The user reported that placing the cursor before typing can sometimes leave the Smartbar without word candidates, while the first keypress suddenly makes candidates appear. Cursor moves and later typing could also produce inconsistent candidate-strip state.

The important trace is now known:

- `KeyboardManager` observes `editorInstance.activeContentFlow` and calls `resetSuggestions(content)` on cursor/content changes;
- therefore cursor movement **does** request candidate recomputation;
- `NlpManager.suggest()` computes candidates asynchronously;
- the old code used `SystemClock.uptimeMillis()` as the request ordering id;
- `internalSuggestions` was also initialized with `SystemClock.uptimeMillis()`;
- a first request made in the same millisecond as initialization could fail `internalSuggestions.first < reqTime` and be discarded;
- cursor movement and a first keypress can likewise enqueue multiple refreshes inside one millisecond, allowing equal request ids and stale/empty UI behavior.

PR #3 replaces timestamp ordering with a strictly monotonic `AtomicLong` generation. The same generation domain is used by:

- async `suggest()`;
- glide/direct candidate publication;
- `clearSuggestions()`.

All three publication paths use the existing `internalSuggestionsGuard`, so an older async result cannot overwrite a newer direct publication/clear after passing an unprotected race window.

A characterization test, `SuggestionRequestSequenceTest.kt`, asserts that many back-to-back generations are distinct and strictly increasing.

### What PR #3 deliberately does not change

- candidate ranking;
- learned-word scoring or promotion thresholds;
- autocorrect thresholds;
- Smartbar overlay priorities;
- dictation UI;
- long-form voice logic.

If the physical-device issue remains after PR #3, continue by tracing composing-region ownership and intentional Smartbar overlays. Do not compensate by forcing candidates over dictation/error/resend/confirmation surfaces.

### Candidate/suggestion files

Start with:

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/Smartbar.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/CandidatesRow.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/LearnedWords.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/WordLearningGate.kt`

## Regression checks

Provider/rewording:

- Automatic rewording OFF → STT text without automatic chat/reword call;
- manual magic wand/translation remains available while automatic rewording is OFF;
- Automatic rewording ON → existing auto-format + auto-apply behavior;
- Realtime OFF → no realtime startup, batch STT remains usable;
- Realtime ON → existing realtime flow;
- OFF/ON does not erase selected model ids;
- Single-call multimodal obeys the automatic-rewording gate.

Learning/suggestions:

- repeated deliberate words and address-like values still learn only when learning is enabled;
- password/private/incognito contexts still never learn;
- likely slips are not promoted aggressively;
- candidate long-press add/forget behavior remains intact;
- first candidate refresh after opening/moving the cursor is not discarded by equal request ids;
- older async candidate results cannot overwrite a newer clear/direct publication;
- intentional dictation/error/resend overlays retain priority.

Run `scripts/architecture-audit.sh`, phone and Wear builds, and the repository unit-test suite/CI before merging runtime changes.

## Handoff rule

Keep this file as current-state documentation. Replace stale pending-work statements when PR #3 lands; do not append a chronological diary. Git history is the archive.
