package ucf.visor.tts

/**
 * What VISOR's words sound like, as handed to a speech engine.
 *
 * VISOR writes its own name in capitals, on screen and in everything it says
 * ("Switched to VISOR colors.", "Say VISOR GO..."). Speech engines read a word
 * in capitals as letters, so users heard "V. I. S. O. R." Its name, and the
 * "VISOR GO" phrase, are said as words; nothing else changes, so text VISOR
 * reads out (a sign that says EXIT) and real initials ("AI") are said as
 * written. The text on screen keeps the capitals.
 *
 * Used for everything [Speaker] says, and for the answer to "Hey Meta, start
 * VISOR" that the glasses say (MainActivity).
 */
object SpokenText {

    private val wakePhrase = Regex("""\bVISOR GO\b""")
    private val name = Regex("""\bVISOR\b""")

    fun forSpeech(text: String): String =
        text.replace(wakePhrase, "Visor go").replace(name, "Visor")
}
