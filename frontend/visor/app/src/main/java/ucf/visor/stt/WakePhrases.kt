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
 * A camera feature a wake phrase starts on its own, with no "VISOR GO" first.
 * Each can be turned off in Settings.
 */
enum class CameraCommand(val phrases: List<String>) {
    READ_TEXT(listOf("VISOR, read this", "VISOR, what does this say", "VISOR, what is this")),
    IDENTIFY_OBJECTS(listOf("VISOR, what am I holding", "VISOR, identify", "VISOR, what is in front of me")),
    DESCRIBE_SCENE(listOf("VISOR, what do you see", "VISOR, describe scene", "VISOR, scene description"));

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
