package ucf.visor.tts

import org.junit.Assert.assertEquals
import org.junit.Test

/** VISOR's name is said as a word, not spelled out; nothing else changes. */
class SpokenTextTest {

    @Test
    fun `VISOR is said as a word`() {
        assertEquals("Switched to Visor colors.", SpokenText.forSpeech("Switched to VISOR colors."))
    }

    @Test
    fun `VISOR's and the end of a sentence`() {
        assertEquals("Visor's AI will use it. Ask Visor.", SpokenText.forSpeech("VISOR's AI will use it. Ask VISOR."))
    }

    @Test
    fun `the wake phrase is said as words`() {
        assertEquals("Say \"Visor go\", then a command.", SpokenText.forSpeech("Say \"VISOR GO\", then a command."))
    }

    // What VISOR reads out loud is said as written, and so are real initials.
    @Test
    fun `other capitals are left alone`() {
        assertEquals("EXIT. Visor's AI. GO SLOW", SpokenText.forSpeech("EXIT. VISOR's AI. GO SLOW"))
    }

    @Test
    fun `only the whole word`() {
        assertEquals("VISORS SUPERVISOR", SpokenText.forSpeech("VISORS SUPERVISOR"))
    }

    // Every spoken phrase VISOR has, from SpokenPhrases and the voice replies,
    // goes through Speaker, so none is spelled out.
    @Test
    fun `no spoken reply keeps VISOR in capitals`() {
        val phrases = listOf(
            ucf.visor.ui.voice.VoiceReplies.VISOR_COLORS_ON,
            ucf.visor.ui.voice.VoiceReplies.CUSTOM_COLORS_ON,
            ucf.visor.ui.voice.VoiceReplies.NO_CUSTOM_COLORS_YET,
            ucf.visor.ui.voice.VoiceReplies.HOME,
            ucf.visor.ui.voice.VoiceReplies.HELP,
        )
        phrases.forEach { assertEquals(false, Regex("""\bVISOR\b""").containsMatchIn(SpokenText.forSpeech(it))) }
    }
}
