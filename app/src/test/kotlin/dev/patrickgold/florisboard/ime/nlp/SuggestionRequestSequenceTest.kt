package dev.patrickgold.florisboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SuggestionRequestSequenceTest : FunSpec({
    test("back-to-back candidate refreshes always have distinct increasing generations") {
        val sequence = SuggestionRequestSequence()
        val ids = List(1024) { sequence.next() }

        ids.toSet().size shouldBe ids.size
        ids.zipWithNext().all { (older, newer) -> newer > older } shouldBe true
    }
})
