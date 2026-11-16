package ucf.visor.capture

/**
 * Takes the user's own words back out of the start of an answer, before it is
 * spoken.
 *
 * Their description (Settings > About you > In your own words) goes to the
 * vision language model with every Describe the scene and Ask a question
 * prompt, so it can choose what to mention (see VisionPlan.sceneGuidance). The
 * prompt asks it not to repeat them, but the smaller models VISOR runs often
 * open with them anyway: "I have glaucoma, sun washes everything out. Ahead is
 * a dark TV screen…". Hearing their own words read back on every picture tells
 * them nothing and delays what they asked for, so they are taken out here.
 *
 * Only an echo at the very start is removed, and only when it repeats their
 * words in order (allowing for "I" becoming "you", and a framing phrase like
 * "In your own words,"). A description that merely shares a word or two with
 * theirs is left alone, so nothing about the scene is ever dropped.
 *
 * Pure Kotlin, so every case is unit-tested (VisionEchoTest).
 */
object VisionEcho {

    /** Fewest of their words, in order, that count as an echo when the model stops partway through them. */
    private const val MinPartialWords = 5

    /** Their words this short are an echo only when they make a whole clause, not the start of one. */
    private const val ShortWords = 4

    /** At most this many framing words ("In your own words, …") before the echo. */
    private const val MaxLeadIn = 8

    /** Words a model may add or leave out while repeating someone ("I have X and Y" for "I have X, Y"). */
    private const val MaxSkips = 2

    private val Word = Regex("[\\p{L}\\p{N}]+(?:'[\\p{L}\\p{N}]+)*")

    /** Words that are the same for matching: a model repeating "I have…" back says "You have…". */
    private val SameWord: Map<String, String> = buildMap {
        listOf("i", "me", "you", "they", "them", "he", "him", "she").forEach { put(it, "@") }
        listOf("my", "your", "their", "his", "her").forEach { put(it, "@s") }
        listOf("myself", "yourself", "themselves", "themself", "himself", "herself").forEach { put(it, "@self") }
        listOf("i'm", "you're", "they're", "he's", "she's").forEach { put(it, "@'re") }
        listOf("i've", "you've", "they've").forEach { put(it, "@'ve") }
        listOf("i'd", "you'd", "they'd", "he'd", "she'd").forEach { put(it, "@'d") }
        listOf("i'll", "you'll", "they'll", "he'll", "she'll").forEach { put(it, "@'ll") }
        listOf("am", "are", "is").forEach { put(it, "be") }
        listOf("was", "were").forEach { put(it, "was") }
        listOf("has", "have").forEach { put(it, "have") }
        listOf("does", "do").forEach { put(it, "do") }
        listOf("doesn't", "don't").forEach { put(it, "don't") }
        listOf("cannot", "can't").forEach { put(it, "can't") }
    }

    /** Words that may come before the echo: "In your own words, …", "You said that …". */
    private val Framing = setOf(
        "in", "@", "@s", "@'ve", "@'re", "own", "words", "word", "vision", "sight", "eyes", "be", "like",
        "this", "said", "say", "says", "mentioned", "told", "tell", "described", "describe", "describes",
        "wrote", "as", "the", "person", "user", "note", "noted", "that", "since", "because", "given",
        "according", "to", "here", "with",
    )

    /** Little words a model adds or drops while repeating someone. */
    private val Filler = setOf("and", "that", "also", "so", "really", "just")

    /** What may be left between the echo and the answer: punctuation and closing quotes. */
    private val LeadingJunk = ".,;:!?\"'”’“‘)]—–-… ".toSet()

    /** A joining word left at the front once the echo is gone: "…everything out, and ahead is…". */
    private val Connector = Regex("^(?:and|so|but|also)\\b[\\s,]*", RegexOption.IGNORE_CASE)

    private class Token(val norm: String, val end: Int)

    /**
     * [answer] without a repeat of [ownWords] at its start, or [answer] as it
     * was when it doesn't start with one. "" when the answer was nothing but
     * the echo, so the caller says it couldn't describe the scene.
     */
    fun strip(answer: String, ownWords: String?): String {
        if (ownWords.isNullOrBlank()) return answer
        val own = tokens(ownWords).map { it.norm }
        // One word ("Glaucoma") is too little to tell an echo from a description.
        if (own.size < 2) return answer
        val said = tokens(answer)
        for (lead in 0..minOf(MaxLeadIn, said.size - 1)) {
            // Every word before the echo must be framing; stop at the first that isn't.
            if (lead > 0 && said[lead - 1].norm !in Framing) break
            val cut = echoEnd(answer, said, lead, own) ?: continue
            return tidy(answer.substring(cut))
        }
        return answer
    }

    /** Where an echo of [own] starting at word [lead] of [said] ends in [answer], or null when there is none. */
    private fun echoEnd(answer: String, said: List<Token>, lead: Int, own: List<String>): Int? {
        var i = lead
        var j = 0
        var matched = 0
        var skips = 0
        var lastMatched = -1
        while (i < said.size && j < own.size) {
            when {
                said[i].norm == own[j] -> { matched++; lastMatched = i; i++; j++ }
                skips < MaxSkips && said[i].norm in Filler -> { skips++; i++ }
                skips < MaxSkips && own[j] in Filler -> { skips++; j++ }
                else -> break
            }
        }
        if (lastMatched < 0) return null
        val end = said[lastMatched].end
        return when {
            j == own.size -> if (own.size >= ShortWords || endsClause(answer, end)) end else null
            matched >= MinPartialWords -> end
            else -> null
        }
    }

    /** True when nothing, or a break in the sentence, comes right after [end]. */
    private fun endsClause(answer: String, end: Int): Boolean {
        val next = answer.substring(end).firstOrNull { !it.isWhitespace() } ?: return true
        return next in ".,;:!?\"'”’)—–-…"
    }

    /** What follows the echo, ready to speak: no leftover punctuation or "and", and a capital first letter. */
    private fun tidy(rest: String): String {
        var text = rest.trimStart { it in LeadingJunk || it.isWhitespace() }
        Connector.find(text)?.let { text = text.substring(it.range.last + 1) }
        return text.trim().replaceFirstChar { it.titlecase() }
    }

    /** The words of [text], lowercased and with [SameWord] applied, each with where it ends. */
    private fun tokens(text: String): List<Token> {
        // Curly apostrophes become straight ones; the length stays the same, so the offsets still fit [text].
        val plain = text.replace('’', '\'').replace('‘', '\'')
        return Word.findAll(plain).map { match ->
            val word = match.value.lowercase()
            Token(SameWord[word] ?: word, match.range.last + 1)
        }.toList()
    }
}
