package ucf.visor.stt

/**
 * VISOR's wake phrases: what [Listener] listens for all the time, as long as
 * one of the commands they start is on.
 *
 * Each must also be in tools/kws/keywords_raw.txt, the keyword model's own list
 * (see tools/kws/README.md); a unit test fails when the two differ. Settings
 * shows these phrases, so what VISOR tells a user to say is always what it
 * listens for.
 */
object WakePhrases {

    /** Opens voice navigation (ui/voice/VoiceNavigationController). */
    const val VOICE_NAVIGATION = "VISOR GO"

    /**
     * [phrase] reduced to its letters, which is how a spotted keyword is
     * compared: the spotter reports its model tokens ("▁VI S OR ▁GO"), and
     * this turns both those and "VISOR, read this" into "VISORREADTHIS".
     */
    fun keyword(phrase: String): String = phrase.uppercase().filter { it.isLetter() }
}

/**
 * One of VISOR's camera features: a picture with the glasses, and an answer.
 * Home has a button for each, so do the glasses, and a wake phrase starts each
 * on its own, with no "VISOR GO" first. Each wake phrase can be turned off in
 * Settings.
 *
 * In the order every list of them shows them, Home's buttons first among
 * them: safety first, then the likeliest to be used.
 *
 * [suggested] is the phrase VISOR suggests when it tells a user what to say.
 */
enum class CameraCommand(val phrases: List<String>, val suggested: String) {
    CHECK_HAZARDS(
        listOf("VISOR, are there hazards in my path", "VISOR, hazard detection", "VISOR, check for hazards"),
        suggested = "VISOR, check for hazards",
    ),
    READ_TEXT(
        listOf("VISOR, read this", "VISOR, what does this say", "VISOR, what is this"),
        suggested = "VISOR, read this",
    ),
    IDENTIFY_OBJECTS(
        listOf("VISOR, what am I holding", "VISOR, identify", "VISOR, what is in front of me"),
        suggested = "VISOR, what am I holding",
    ),
    DESCRIBE_SCENE(
        listOf("VISOR, what do you see", "VISOR, describe scene", "VISOR, scene description"),
        suggested = "VISOR, what do you see",
    );

    companion object {
        /** The command a spotted [keyword] starts, or null (for "VISOR GO", say). */
        fun forKeyword(keyword: String): CameraCommand? {
            val spotted = WakePhrases.keyword(keyword)
            return entries.firstOrNull { command ->
                command.phrases.any { WakePhrases.keyword(it) == spotted }
            }
        }
    }
}
