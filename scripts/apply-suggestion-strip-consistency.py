from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NLP = ROOT / "app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt"


def require_count(text: str, needle: str, count: int, label: str) -> None:
    actual = text.count(needle)
    if actual != count:
        raise SystemExit(f"{label}: expected {count} match(es), found {actual}: {needle!r}")


text = NLP.read_text(encoding="utf-8")

# Candidate refreshes can be issued several times in the same millisecond: cursor movement,
# the first keypress and a Smartbar state change may all arrive back-to-back. uptimeMillis()
# therefore cannot be a request id. It can even equal the timestamp used to initialise the
# state, which makes the very first result fail the strict 'older < newer' test.
require_count(text, "import android.os.SystemClock\n", 1, "SystemClock import")
text = text.replace("import android.os.SystemClock\n", "", 1)

require_count(text, "import java.util.concurrent.atomic.AtomicBoolean\n", 1, "AtomicBoolean import")
text = text.replace(
    "import java.util.concurrent.atomic.AtomicBoolean\n",
    "import java.util.concurrent.atomic.AtomicBoolean\nimport java.util.concurrent.atomic.AtomicLong\n",
    1,
)

sequence_marker = "private const val BLANK_STR_PATTERN"
require_count(text, sequence_marker, 1, "request sequence insertion marker")
sequence_class = '''/**
 * Strictly increasing ids for candidate refreshes. A clock is not an ordering primitive here: cursor
 * movement and a first keypress can legitimately enqueue more than one refresh in one millisecond.
 */
internal class SuggestionRequestSequence {
    private val counter = AtomicLong(0L)

    fun next(): Long = counter.incrementAndGet()
}

'''
text = text.replace(sequence_marker, sequence_class + sequence_marker, 1)

state_marker = "    private val internalSuggestionsGuard = Mutex()\n"
require_count(text, state_marker, 1, "internal suggestion state marker")
text = text.replace(
    state_marker,
    "    private val suggestionRequestSequence = SuggestionRequestSequence()\n" + state_marker,
    1,
)

initial_clock = "Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>())"
require_count(text, initial_clock, 1, "initial suggestion generation")
text = text.replace(
    initial_clock,
    "Delegates.observable(0L to listOf<SuggestionCandidate>())",
    1,
)

allocation = "val reqTime = SystemClock.uptimeMillis()"
require_count(text, allocation, 3, "candidate request-id allocation")
text = text.replace(allocation, "val reqGeneration = suggestionRequestSequence.next()")

# Rename the remaining references belonging to those three allocations. There are no other reqTime
# values in this file; fail below if one ever appears so the patch cannot silently become partial.
text = text.replace("reqTime", "reqGeneration")

# Async suggest() already publishes under the mutex. Direct glide publications and clears must use the
# same mutex too, otherwise an async producer can pass its generation check and be overwritten midway.
direct_line = "            internalSuggestions = reqGeneration to if (wanted) suggestions else emptyList()\n"
require_count(text, direct_line, 1, "direct suggestion publication")
text = text.replace(
    direct_line,
    '''            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqGeneration) {
                    internalSuggestions = reqGeneration to if (wanted) suggestions else emptyList()
                }
            }
''',
    1,
)

clear_line = "            internalSuggestions = reqGeneration to emptyList()\n"
require_count(text, clear_line, 1, "clear suggestion publication")
text = text.replace(
    clear_line,
    '''            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqGeneration) {
                    internalSuggestions = reqGeneration to emptyList()
                }
            }
''',
    1,
)

if "SystemClock" in text or "reqTime" in text:
    raise SystemExit("stale timestamp-based candidate ordering remains")
require_count(text, "suggestionRequestSequence.next()", 3, "monotonic request-id allocation")
require_count(text, "internalSuggestions.first < reqGeneration", 3, "generation guard")
NLP.write_text(text, encoding="utf-8")

# Characterization test: elapsed time is irrelevant; every back-to-back refresh must still be strictly newer.
test = ROOT / "app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/SuggestionRequestSequenceTest.kt"
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(
    '''package dev.patrickgold.florisboard.ime.nlp

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
''',
    encoding="utf-8",
)

print("Suggestion strip request-ordering patch applied successfully")
