package ucf.visor.ui.screens.profile

import ucf.visor.data.Severity
import ucf.visor.data.SpeechRates
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionType

/**
 * How setup (ProfileCreationScreen) understands a spoken answer to each of
 * its questions. Kept apart from the screen so it can be tested: a misheard
 * answer here changes a user's settings without them seeing it happen.
 *
 * Whole words are matched, not parts of words: "no" must not be heard in
 * "know" or "not sure", and "yes, I know" must not turn voice navigation off.
 */
internal object OnboardingAnswers {

    /**
     * What a user says to leave a question as it is and move on, as the Skip
     * button does. Only the whole answer counts, so a description that happens
     * to start "next to nothing..." is kept as a description.
     */
    private val skipPhrases = setOf(
        "skip", "skip it", "skip this", "skip that", "skip this one", "skip for now",
        "skip this question", "next", "next question", "continue", "pass",
    )

    /** Said at the last step to finish setup, as the Start using VISOR button does. */
    private val startWords = setOf("start", "begin", "done", "finish", "finished", "continue", "next")

    // "Keep it on" and "turn it off" decide by their last word; "keep" alone
    // decides nothing, as in "no, keep it off".
    private val yesWords = setOf("yes", "yeah", "yep", "yup", "sure", "okay", "ok", "on")
    private val noWords = setOf("no", "nope", "nah", "off")

    /** Said before a name, as in "call me Sam": not part of the name. */
    private val namePrefixes = listOf("my name is", "you can call me", "call me", "i am", "i'm", "it's", "it is")

    fun isSkip(heard: String): Boolean = normalize(heard) in skipPhrases

    fun isStart(heard: String): Boolean = words(heard).any { it in startWords }

    /**
     * The name in [heard], without "my name is" or "call me" before it, or
     * null if nothing is left.
     */
    fun name(heard: String): String? {
        var name = heard.trim().trimEnd('.', '!')
        val lower = name.lowercase()
        namePrefixes.firstOrNull { lower.startsWith("$it ") }?.let { prefix ->
            name = name.substring(prefix.length).trim()
        }
        return name.ifBlank { null }
    }

    /** True for yes, false for no, or null if it was neither, or both. */
    fun yesNo(heard: String): Boolean? {
        val said = words(heard)
        val yes = said.any { it in yesWords }
        val no = said.any { it in noWords }
        return when {
            yes && !no -> true
            no && !yes -> false
            else -> null
        }
    }

    /**
     * Every kind of vision loss named in [heard] (more than one can be), or
     * null if none was. "Not sure" counts only when nothing else was named.
     */
    fun visionTypes(heard: String): Set<VisionType>? {
        val text = heard.lowercase()
        val said = words(heard)
        val found = buildSet {
            if ("center" in text || "central" in text || "middle" in said) add(VisionType.CENTRAL_LOSS)
            if ("side" in said || "sides" in said || "peripheral" in text || "edges" in said) {
                add(VisionType.PERIPHERAL_LOSS)
            }
            if ("blur" in text) add(VisionType.BLUR_LOW_ACUITY)
            if ("light" in said || "contrast" in text || "glare" in said) add(VisionType.CONTRAST_LIGHT)
        }
        return when {
            found.isNotEmpty() -> found
            "not sure" in text || "don't know" in text || "unsure" in said -> setOf(VisionType.NOT_SURE)
            else -> null
        }
    }

    fun severity(heard: String): Severity? {
        val said = words(heard)
        return when {
            "little" in said || "bit" in said -> Severity.MILD
            "moderate" in said || "medium" in said -> Severity.MODERATE
            "lot" in said -> Severity.SEVERE
            else -> null
        }
    }

    fun speechRate(heard: String): Float? {
        val text = heard.lowercase()
        return when {
            "slow" in text -> SpeechRates.Slow
            "fast" in text -> SpeechRates.Fast
            "normal" in text -> SpeechRates.Normal
            else -> null
        }
    }

    fun verbosity(heard: String): Verbosity? {
        val text = heard.lowercase()
        return when {
            "brief" in text -> Verbosity.BRIEF
            "detail" in text -> Verbosity.DETAILED
            "standard" in text -> Verbosity.STANDARD
            else -> null
        }
    }

    /** [heard] in lower case, its words separated by single spaces, without punctuation. */
    private fun normalize(heard: String): String =
        heard.lowercase().replace(Regex("[^a-z' ]"), " ").trim().replace(Regex(" +"), " ")

    private fun words(heard: String): List<String> = normalize(heard).split(' ').filter { it.isNotEmpty() }
}
