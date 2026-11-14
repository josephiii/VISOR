package ucf.visor.background

/**
 * What background listening's notification says. Pure, so the accessibility
 * rules below are unit-tested (ListeningNotificationTextTest):
 *
 * - **Words, never only an icon or a colour** (Section 508: Perceivable,
 *   Understandable). The microphone, the glasses' battery and "low" are all
 *   written out; "40%" is read "40 percent" by TalkBack.
 * - **Understood on its own.** An answer is introduced by the feature that gave
 *   it ("Last answer, Read text: …"), as Home's Last answer card does.
 * - **Nothing private on the lock screen.** An answer may be text read off
 *   someone's mail or bank statement, so the lock-screen version
 *   ([Content.publicTitle], [Content.publicText]) only says VISOR is listening
 *   and how the glasses' battery is.
 * - **Short.** A long answer is cut at a word, well before Android's own limit,
 *   and says where the rest is, rather than ending mid-sentence.
 *
 * How it is shown (ListeningNotification) adds the rest: the standard
 * template only, so it follows the phone's font size, contrast and dark theme;
 * silent, and alerting only once, so TalkBack does not read every update over
 * VISOR's own voice; and text-labelled actions.
 */
object ListeningNotificationText {

    /** What a camera feature last answered: its name ("Read text"), the answer, and when. */
    data class Answer(val feature: String, val text: String, val atMillis: Long? = null)

    /** The glasses VISOR would use, as far as the SDK knows. */
    data class Glasses(
        val connected: Boolean,
        val batteryPercent: Int?,
        val charging: Boolean?,
        val worn: Boolean?,
    )

    data class Content(
        val title: String,
        /** The one line shown when the notification is collapsed. */
        val text: String,
        /** Everything, shown when it is expanded. */
        val bigText: String,
        /** Beside the app name in the header: the glasses' battery, when known. */
        val subText: String?,
        val publicTitle: String,
        val publicText: String,
        /** When the answer was given, for the notification's time stamp; null with no answer. */
        val answeredAtMillis: Long? = null,
    )

    const val TITLE = "VISOR is listening"
    const val HINT = "Say \"VISOR GO\" or a camera phrase."
    const val MORE_IN_VISOR = "Open VISOR for the rest."

    /** First, ahead of everything else, while VISOR has lost its link to the glasses. */
    const val LINK_LOST = "Lost the glasses connection. Tap here to reconnect."

    /** At or below this, and not charging, the battery is said to be low. */
    const val LOW_BATTERY_PERCENT = 20

    /** An answer longer than this is cut at a word: a notification is not for reading a page. */
    const val MAX_ANSWER_CHARS = 400

    fun build(
        throughGlasses: Boolean,
        answer: Answer?,
        glasses: Glasses?,
        linkLost: Boolean = false,
    ): Content {
        val title = if (throughGlasses) "$TITLE through your glasses" else "$TITLE through your phone"
        val glassesLine = glasses?.let(::glassesLine)
        val answerLine = answer?.let { "Last answer, ${it.feature}: ${shorten(it.text)}" }

        // A lost link comes first: it is what to do next, and tapping fixes it.
        val lead = if (linkLost) LINK_LOST else answerLine ?: HINT
        val bigText = listOfNotNull(lead, answerLine.takeIf { linkLost }, glassesLine).joinToString("\n")
        return Content(
            title = title,
            text = lead,
            bigText = bigText,
            subText = glasses?.let(::batteryShort),
            publicTitle = TITLE,
            publicText = if (linkLost) LINK_LOST else glassesLine ?: HINT,
            answeredAtMillis = answer?.atMillis,
        )
    }

    /** "Glasses battery 40%, charging." and the like: one sentence, facts the SDK knows only. */
    fun glassesLine(glasses: Glasses): String {
        if (!glasses.connected) return "Glasses not connected."
        val parts = buildList {
            glasses.batteryPercent?.let { level ->
                add(
                    when {
                        glasses.charging == true -> "battery $level%, charging"
                        level <= LOW_BATTERY_PERCENT -> "battery low, $level%"
                        else -> "battery $level%"
                    }
                )
            }
            if (glasses.worn == false) add("not being worn")
        }
        return if (parts.isEmpty()) "Glasses connected." else "Glasses ${parts.joinToString(", ")}."
    }

    private fun batteryShort(glasses: Glasses): String? {
        if (!glasses.connected) return "Glasses not connected"
        val level = glasses.batteryPercent ?: return null
        return "Glasses $level%"
    }

    /** [text] whole, or cut at a word before [MAX_ANSWER_CHARS] with where to find the rest. */
    fun shorten(text: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.length <= MAX_ANSWER_CHARS) return clean
        val cut = clean.take(MAX_ANSWER_CHARS)
        val atWord = cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ':', ' ')
        return "$atWord… $MORE_IN_VISOR"
    }
}

/**
 * When to say the glasses' battery is running low, with the screen off: the
 * notification alone can't be seen from a pocket. Once as it falls to each of
 * [THRESHOLDS], not again until it has been charged.
 */
object BatteryAlerts {
    val THRESHOLDS = listOf(20, 10)

    /** How much above a threshold the battery must climb before that threshold is said again. */
    private const val RESET_MARGIN = 5

    /**
     * The threshold to say now, or null; and the lowest threshold said so far,
     * to pass back next time ([lastSaid] null when none has been).
     */
    fun next(lastSaid: Int?, percent: Int?, charging: Boolean?): Pair<Int?, Int?> {
        if (percent == null) return null to lastSaid
        if (charging == true) return null to null
        val reached = THRESHOLDS.filter { percent <= it }.minOrNull()
        val stillSaid = lastSaid?.takeIf { percent < it + RESET_MARGIN }
        return if (reached != null && (stillSaid == null || reached < stillSaid)) {
            reached to reached
        } else {
            null to stillSaid
        }
    }

    /** Said with the battery's actual level, which may already be below the threshold reached. */
    fun spoken(percent: Int): String = "Your glasses battery is low, at $percent percent."
}
