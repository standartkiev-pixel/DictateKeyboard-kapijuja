/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app

import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Locks the quiet-typing and clipboard defaults chosen for clean Kapijuja installations. */
class KapijujaDefaultPreferencesTest : FunSpec({
    test("clipboard defaults match the Kapijuja setup") {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)

        assertSoftly {
            prefs.clipboard.useInternalClipboard.get() shouldBe true
            prefs.clipboard.syncToFloris.get() shouldBe ClipboardSyncBehavior.ALL_EVENTS
            prefs.clipboard.syncToSystem.get() shouldBe ClipboardSyncBehavior.ALL_EVENTS
            prefs.clipboard.stripTrackingParams.get() shouldBe false
            prefs.clipboard.trimOnCopy.get() shouldBe true
            prefs.clipboard.suggestionEnabled.get() shouldBe true
            prefs.clipboard.suggestionTimeout.get() shouldBe 300
            prefs.clipboard.historyEnabled.get() shouldBe true
            prefs.clipboard.historyAutoCleanOldEnabled.get() shouldBe false
            prefs.clipboard.historyAutoCleanSensitiveEnabled.get() shouldBe false
            prefs.clipboard.historySizeLimitEnabled.get() shouldBe true
            prefs.clipboard.historySizeLimit.get() shouldBe 90
            prefs.clipboard.historyHideOnPaste.get() shouldBe false
            prefs.clipboard.historyHideOnNextTextField.get() shouldBe true
            prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.get() shouldBe true
        }
    }

    test("automatic text changes and suggestions default off") {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)

        assertSoftly {
            prefs.correction.autoCapitalization.get() shouldBe false
            prefs.correction.autoSpacePunctuation.get() shouldBe false
            prefs.correction.tightenPunctuationSpacing.get() shouldBe false
            prefs.correction.doubleSpacePeriod.get() shouldBe false
            prefs.suggestion.api30InlineSuggestionsEnabled.get() shouldBe false
            prefs.suggestion.enabled.get() shouldBe false
            prefs.suggestion.autoCorrect.get() shouldBe false
            prefs.suggestion.multilingualTyping.get() shouldBe false
            prefs.suggestion.nextWordPrediction.get() shouldBe false
            prefs.suggestion.learnTypedWords.get() shouldBe false
            prefs.suggestion.mathSuggestions.get() shouldBe false
            prefs.emoji.suggestionEnabled.get() shouldBe false
            prefs.dictionary.enableSystemUserDictionary.get() shouldBe false
            prefs.dictionary.enableFlorisUserDictionary.get() shouldBe false
            prefs.spelling.useContacts.get() shouldBe false
            prefs.spelling.useUdmEntries.get() shouldBe false
        }
    }

    test("swipe and glide typing defaults off but long presses remain usable") {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)

        assertSoftly {
            prefs.gestures.swipeUp.get() shouldBe SwipeAction.NO_ACTION
            prefs.gestures.swipeDown.get() shouldBe SwipeAction.NO_ACTION
            prefs.gestures.swipeLeft.get() shouldBe SwipeAction.NO_ACTION
            prefs.gestures.swipeRight.get() shouldBe SwipeAction.NO_ACTION
            prefs.gestures.spaceBarSwipeLeft.get() shouldBe SwipeAction.NO_ACTION
            prefs.gestures.spaceBarSwipeRight.get() shouldBe SwipeAction.NO_ACTION
            prefs.gestures.deleteKeySwipeLeft.get() shouldBe SwipeAction.NO_ACTION
            prefs.glide.enabled.get() shouldBe false
            prefs.glide.showTrail.get() shouldBe false
            prefs.glide.showPreview.get() shouldBe false
            prefs.glide.immediateBackspaceDeletesWord.get() shouldBe false
        }
    }
})
