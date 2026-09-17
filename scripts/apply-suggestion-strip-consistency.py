from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_exact(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}\n--- pattern ---\n{old}")
    return text.replace(old, new, 1)


nlp = "app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt"
path = ROOT / nlp
text = path.read_text(encoding="utf-8")

# Candidate refreshes can be issued several times in the same millisecond: cursor movement,
# the first keypress and a Smartbar state change may all arrive back-to-back. uptimeMillis()
# gave those requests the same id, so whichever asynchronous request finished first could
# become impossible for the actually newer request to replace. Use an explicit monotonic
# sequence instead, and serialize every publication through the same mutex.
text = replace_exact(
    text,
    "import android.os.SystemClock\n",
    "",
    "SystemClock import",
)
text = replace_exact(
    text,
    "import java.util.concurrent.atomic.AtomicBoolean\n",
    "import java.util.concurrent.atomic.AtomicBoolean\nimport java.util.concurrent.atomic.AtomicLong\n",
    "AtomicLong import",
)
text = replace_exact(
    text,
    'private const val BLANK_STR_PATTERN = "^\\\\s*$"\n',
    '''private const val BLANK_STR_PATTERN = "^\\\\s*$"\n\n/**\n * Strictly increasing ids for candidate refreshes. Wall/uptime clocks are deliberately not used here:\n * cursor movement and a first keypress can legitimately enqueue more than one refresh in one millisecond.\n */\ninternal class SuggestionRequestSequence {\n    private val counter = AtomicLong(0L)\n\n    fun next(): Long = counter.incrementAndGet()\n}\n''',
    "request sequence insertion point",
)
text = replace_exact(
    text,
    '''    private val internalSuggestionsGuard = Mutex()\n    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->\n        scope.launch { assembleCandidates() }\n    }\n''',
    '''    private val suggestionRequestSequence = SuggestionRequestSequence()\n    private val internalSuggestionsGuard = Mutex()\n    private var internalSuggestions by Delegates.observable(0L to listOf<SuggestionCandidate>()) { _, _, _ ->\n        scope.launch { assembleCandidates() }\n    }\n''',
    "internal suggestion generation state",
)

# There are exactly three request-id allocations: asynchronous suggest(), glide/direct publish,
# and clearSuggestions(). Keep them all in one ordering domain.
old_alloc = "val reqTime = SystemClock.uptimeMillis()"
count = text.count(old_alloc)
if count != 3:
    raise SystemExit(f"{nlp}: expected three uptime request ids, found {count}")
text = text.replace(old_alloc, "val reqGeneration = suggestionRequestSequence.next()")
text = text.replace("internalSuggestions.first < reqTime", "internalSuggestions.first < reqGeneration")
text = text.replace("internalSuggestions = reqTime to when", "internalSuggestions = reqGeneration to when")

text = replace_exact(
    text,
    '''        runBlocking {\n            internalSuggestions = reqGeneration to if (wanted) suggestions else emptyList()\n        }\n''',
    '''        runBlocking {\n            internalSuggestionsGuard.withLock {\n                if (internalSuggestions.first < reqGeneration) {\n                    internalSuggestions = reqGeneration to if (wanted) suggestions else emptyList()\n                }\n            }\n        }\n''',
    "direct suggestion publication",
)
text = replace_exact(
    text,
    '''        runBlocking {\n            internalSuggestions = reqGeneration to emptyList()\n        }\n''',
    '''        runBlocking {\n            internalSuggestionsGuard.withLock {\n                if (internalSuggestions.first < reqGeneration) {\n                    internalSuggestions = reqGeneration to emptyList()\n                }\n            }\n        }\n''',
    "clear suggestion publication",
)

if "SystemClock" in text or "reqTime" in text:
    raise SystemExit(f"{nlp}: stale timestamp request ordering remains")
path.write_text(text, encoding="utf-8")

# Small characterization test: the invariant we need is not elapsed time, only strict ordering.
test = ROOT / "app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/SuggestionRequestSequenceTest.kt"
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(
    '''package dev.patrickgold.florisboard.ime.nlp\n\nimport io.kotest.core.spec.style.FunSpec\nimport io.kotest.matchers.shouldBe\n\nclass SuggestionRequestSequenceTest : FunSpec({\n    test("back-to-back candidate refreshes always have distinct increasing generations") {\n        val sequence = SuggestionRequestSequence()\n        val ids = List(1024) { sequence.next() }\n\n        ids.toSet().size shouldBe ids.size\n        ids.zipWithNext().all { (older, newer) -> newer > older } shouldBe true\n    }\n})\n''',
    encoding="utf-8",
)

print("Suggestion strip request-ordering patch applied successfully")
