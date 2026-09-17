/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.glideTypingManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.LearnedWordsStore
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionType
import dev.patrickgold.florisboard.ime.media.emoji.emojiQuerySource
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.math.Calculator
import dev.patrickgold.florisboard.ime.nlp.math.MathSuggestionCandidate
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

// Frequency stored for a word learned from the suggestion strip (issue #241) — the maximum, matching what
// the user dictionary settings screen assigns to a hand-added word.
private const val USER_DICTIONARY_FREQ = 255

/**
 * Whether the word provider should be asked at all, given the user's "Display suggestions" switch and
 * whether the active provider insists (issue #297).
 *
 * Its own decision, because [NlpManager.isSuggestionOn] is not one: that is an OR across three unrelated
 * features, and using it as the single gate meant emoji suggestions — on by default — held the door open
 * for the word suggestions the user had just switched off.
 *
 * The exception is not a courtesy. A shape-based provider *types* through its candidate list: switching
 * that off does not declutter the strip, it takes the language away. So the override lives here, beside
 * the rule it overrides, instead of waiting to be rediscovered the next time someone tidies this up.
 */
internal fun wantsWordSuggestions(
    displaySuggestions: Boolean,
    providerForcesSuggestionOn: Boolean,
): Boolean = displaySuggestions || providerForcesSuggestionOn

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()
    // Kept as the Lazy rather than unwrapped with `by`: the glide manager reaches back for this very
    // NlpManager, so it must not be built while this one is still being constructed.
    private val glideTypingManager = context.glideTypingManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Whether a selection was already running the last time the Smartbar's expanded state was decided, so
     * the start of one can be told apart from a change to one (issue #335).
     */
    private var wasSelectionActive = false

    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    private val internalSuggestionsGuard = Mutex()
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableStateFlow(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            // Cleared, not merely reassembled: the words from before the flip are still sitting in
            // [internalSuggestions], and reassembling publishes them straight back — the strip would stay
            // exactly as it was until the next keystroke, which is what made switching this off look like
            // it did nothing (issue #297). Clearing reassembles by itself.
            clearSuggestions()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.mathSuggestions.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule from the subtype resources.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value[subtype.punctuationRule] ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
        }
    }

    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = true,
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun continuesWord(composingWord: String, char: Char): Boolean {
        if (composingWord.isEmpty()) return false
        val subtype = subtypeManager.activeSubtype
        return runBlocking { getSuggestionProvider(subtype) }.continuesWord(composingWord, char)
    }

    fun standaloneCapitalization(word: String): String? {
        if (word.isEmpty()) return null
        val subtype = subtypeManager.activeSubtype
        return runBlocking { getSuggestionProvider(subtype) }.standaloneCapitalization(word, subtype)
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking { getSuggestionProvider(subtype).forcesSuggestionOn }
        }
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    fun wordSuggestionsWanted(): Boolean = wantsWordSuggestions(
        displaySuggestions = prefs.suggestion.enabled.get(),
        providerForcesSuggestionOn = providerForcesSuggestionOn(subtypeManager.activeSubtype),
    )

    @Volatile
    private var holdNextSuggest = false

    fun suggest(subtype: Subtype, content: EditorContent) {
        if (holdNextSuggest) {
            holdNextSuggest = false
            return
        }
        val reqTime = SystemClock.uptimeMillis()
        scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> emojiSuggestionProvider.suggest(
                    subtype = subtype,
                    content = content,
                    maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                    allowPossiblyOffensive = true,
                    isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                )
                else -> emptyList()
            }
            val emojiSearch = emojiQuerySource(content.composingText, content.currentWordText)
                .startsWith(EmojiSuggestionType.LEADING_COLON.prefix)
            val suggestions = when {
                !wordSuggestionsWanted() -> emptyList()
                emojiSuggestions.isNotEmpty() && emojiSearch -> emptyList()
                else -> {
                    val provider = getSuggestionProvider(subtype)
                    provider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = provider.maxCandidates,
                        allowPossiblyOffensive = true,
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
            }
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqTime) {
                    internalSuggestions = reqTime to when {
                        emojiSuggestions.isEmpty() -> suggestions
                        emojiSearch -> emojiSuggestions + suggestions
                        else -> suggestions + emojiSuggestions
                    }
                }
            }
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>, holdNext: Boolean = false) {
        val wanted = wordSuggestionsWanted()
        val reqTime = SystemClock.uptimeMillis()
        holdNextSuggest = holdNext
        runBlocking {
            internalSuggestions = reqTime to if (wanted) suggestions else emptyList()
        }
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestions = reqTime to emptyList()
        }
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        return activeCandidates.firstOrNull { it.isEligibleForAutoCommit }
    }

    enum class AddToDictionaryResult { ADDED, ALREADY_PRESENT, UNAVAILABLE }

    fun addToUserDictionary(subtype: Subtype, candidate: SuggestionCandidate): AddToDictionaryResult {
        val word = candidate.text.toString().trim()
        if (word.isEmpty()) return AddToDictionaryResult.UNAVAILABLE
        val dao = DictionaryManager.default().florisUserDictionaryDao()
            ?: return AddToDictionaryResult.UNAVAILABLE
        val locale = subtype.primaryLocale
        return runCatching {
            if (dao.queryExactFuzzyLocale(word, locale).isNotEmpty()) {
                AddToDictionaryResult.ALREADY_PRESENT
            } else {
                dao.insert(
                    UserDictionaryEntry(
                        id = 0,
                        word = word,
                        freq = USER_DICTIONARY_FREQ,
                        locale = locale.localeTag(),
                        shortcut = null,
                    )
                )
                scope.launch {
                    notePersonalVocabularyChanged(subtype)
                    suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                }
                glideTypingManager.value.invalidateWordData()
                AddToDictionaryResult.ADDED
            }
        }.getOrDefault(AddToDictionaryResult.UNAVAILABLE)
    }

    fun learnFinishedWord(
        word: String,
        origin: WordOrigin,
        tapPoints: FloatArray?,
        weight: Int = 1,
        trustedByUser: Boolean = false,
    ) {
        if (word.isBlank() || !prefs.suggestion.learnTypedWords.get()) return
        val subtype = subtypeManager.activeSubtype
        val isPrivate = keyboardManager.activeState.isIncognitoMode || !wordSuggestionsWanted()
        scope.launch {
            val provider = getSuggestionProvider(subtype) as? LearningProvider ?: return@launch
            val outcome = provider.learnTypedWord(
                subtype = subtype,
                word = word,
                origin = origin,
                tapPoints = tapPoints,
                isPrivateSession = isPrivate,
                weight = weight,
                trustedByUser = trustedByUser,
            )
            if (!outcome.learned) return@launch
            if (outcome.readyForPromotion) promoteLearnedWord(subtype, outcome)
            suggest(subtype, editorInstance.activeContent)
        }
    }

    private suspend fun promoteLearnedWord(subtype: Subtype, outcome: LearnOutcome) {
        val dao = DictionaryManager.default().florisUserDictionaryDao() ?: return
        val locale = subtype.primaryLocale
        val promoted = runCatching {
            if (dao.queryExactFuzzyLocale(outcome.word, locale).isEmpty()) {
                dao.insert(
                    UserDictionaryEntry(
                        id = 0,
                        word = outcome.word,
                        freq = USER_DICTIONARY_FREQ,
                        locale = locale.localeTag(),
                        shortcut = null,
                    )
                )
            }
            true
        }.getOrDefault(false)
        if (!promoted) return
        LearnedWordsStore.setPromoted(appContext, outcome.entryId, true, outcome.lang)
        notePersonalVocabularyChanged(subtype)
        glideTypingManager.value.invalidateWordData()
    }

    fun forgetLearnedWord(subtype: Subtype, candidate: SuggestionCandidate) {
        val word = candidate.text.toString().trim()
        if (word.isEmpty()) return
        scope.launch {
            val provider = getSuggestionProvider(subtype) as? LearningProvider ?: return@launch
            val wasPromoted = provider.forgetLearnedWord(subtype, word)
            if (wasPromoted) {
                val dao = DictionaryManager.default().florisUserDictionaryDao()
                runCatching {
                    dao?.queryExactFuzzyLocale(word, subtype.primaryLocale)?.forEach { dao.delete(it) }
                }
                notePersonalVocabularyChanged(subtype)
                glideTypingManager.value.invalidateWordData()
            }
            suggest(subtype, editorInstance.activeContent)
        }
    }

    private suspend fun notePersonalVocabularyChanged(subtype: Subtype) {
        (getSuggestionProvider(subtype) as? LearningProvider)?.onPersonalVocabularyChanged()
    }

    fun learnWordPair(previousWord: String, word: String) {
        if (previousWord.isBlank() || word.isBlank() || !prefs.suggestion.learnTypedWords.get()) return
        if (keyboardManager.activeState.isIncognitoMode) return
        val subtype = subtypeManager.activeSubtype
        scope.launch {
            (getSuggestionProvider(subtype) as? LearningProvider)?.learnWordPair(subtype, previousWord, word)
        }
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return runBlocking { candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true }.also { result ->
            if (result) {
                scope.launch {
                    if (candidate is ClipboardSuggestionCandidate) {
                        assembleCandidates()
                    } else {
                        suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                    }
                }
            }
        }
    }

    fun getListOfWords(subtype: Subtype): List<String> {
        return runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
    }

    private fun mathCandidates(): List<SuggestionCandidate> {
        if (!prefs.suggestion.mathSuggestions.get()) return emptyList()
        val state = keyboardManager.activeState
        if (state.keyVariation == KeyVariation.PASSWORD) return emptyList()
        if (editorInstance.activeInfo.isRawInputEditor) return emptyList()
        val result = Calculator.evaluateTrailing(
            textBeforeCursor = editorInstance.activeContent.textBeforeSelection,
            locale = subtypeManager.activeSubtype.primaryLocale.base,
        ) ?: return emptyList()
        return listOf(MathSuggestionCandidate(result))
    }

    private fun assembleCandidates() {
        runBlocking {
            val candidates = when {
                isSuggestionOn() -> mathCandidates().ifEmpty {
                    clipboardSuggestionProvider.suggest(
                        subtype = Subtype.DEFAULT,
                        content = editorInstance.activeContent,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = true,
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    ).ifEmpty {
                        buildList {
                            internalSuggestionsGuard.withLock {
                                addAll(internalSuggestions.second)
                            }
                        }
                    }
                }
                else -> emptyList()
            }
            activeCandidates = candidates
            autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
        }
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        if (!prefs.smartbar.enabled.get()) {
            return
        }
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val selectionJustStarted = isSelection && !wasSelectionActive
        wasSelectionActive = isSelection
        if (isSelection && prefs.smartbar.selectionMetrics.get()) {
            if (selectionJustStarted && prefs.smartbar.sharedActionsExpanded.get()) {
                scope.launch {
                    prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
                    prefs.smartbar.sharedActionsExpanded.set(false)
                }
            }
            return
        }

        // Candidate generation is asynchronous. A cursor move into an existing word therefore has a
        // short interval where the new current word is already known but its candidates are not. The old
        // rule treated that transient empty list as a reason to expand the actions row; by the time the
        // candidates arrived the user saw the toolbar jump and, on some hosts, the word suggestions stayed
        // visually hidden until another key was pressed. Keep the candidate surface reserved while the
        // cursor is on a word. Empty-field/finished-sentence behaviour is unchanged and still gives the
        // row back to quick actions.
        val editingWord = !isSelection &&
            wordSuggestionsWanted() &&
            editorInstance.activeContent.currentWordText.isNotBlank()
        val noCandidates = list1.isNullOrEmpty() && list2.isNullOrEmpty()
        val isExpanded = (noCandidates && !editingWord) || isSelection
        if (prefs.smartbar.sharedActionsExpanded.get() != isExpanded) {
            scope.launch {
                prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
                prefs.smartbar.sharedActionsExpanded.set(isExpanded)
            }
        }
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.update { it + 1 }
    }

    fun clearDebugOverlay() {
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.update { it + 1 }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(true)) provider.destroy()
        }
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

        override val providerId = "org.florisboard.nlp.providers.clipboard"

        override suspend fun create() = Unit

        override suspend fun preload(subtype: Subtype) = Unit

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(ClipboardSuggestionCandidate(currentItem, sourceProvider = this@ClipboardSuggestionProvider, context = context))
                    if (currentItem.isSensitive) return@buildList
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(
                                    ClipboardSuggestionCandidate(
                                        clipboardItem = currentItem.copy(
                                            text = if (match.value.startsWith("(") && match.value.endsWith(")")) {
                                                match.value.substring(1, match.value.length - 1)
                                            } else {
                                                match.value
                                            }
                                        ),
                                        sourceProvider = this@ClipboardSuggestionProvider,
                                        context = context,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) = Unit

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun getListOfWords(subtype: Subtype): List<String> = emptyList()

        override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double = 0.0

        override suspend fun destroy() = Unit

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                it.id != lastItemId &&
                    contentText.isBlank() &&
                    !currentItem.text.isNullOrBlank() &&
                    !blankStrRegex.matches(currentItem.text)
            }
    }
}
