package ucf.visor.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The wake phrases: which camera feature each starts, and that the app (and so
 * Settings, which lists them) and the keyword model listen for the same ones.
 */
class WakePhrasesTest {

    @Test
    fun aSpottedKeywordStartsItsCameraCommand() {
        // As the spotter reports it: the model's tokens, not the plain phrase.
        assertEquals(CameraCommand.READ_TEXT, CameraCommand.forKeyword("▁VI S OR ▁RE AD ▁THIS"))
        assertEquals(CameraCommand.IDENTIFY_OBJECTS, CameraCommand.forKeyword("VISOR WHAT AM I HOLDING"))
        assertEquals(CameraCommand.DESCRIBE_SCENE, CameraCommand.forKeyword("VISOR, what do you see?"))
    }

    @Test
    fun visorGoIsNotACameraCommand() {
        assertNull(CameraCommand.forKeyword("▁VI S OR ▁GO"))
        assertEquals("VISORGO", WakePhrases.keyword(WakePhrases.VOICE_NAVIGATION))
    }

    @Test
    fun theAppListensForExactlyThePhrasesTheKeywordModelKnows() {
        val app = (CameraCommand.entries.flatMap { it.phrases } + WakePhrases.VOICE_NAVIGATION)
            .map(WakePhrases::keyword)
            .toSet()
        val model = keywordModelPhrases()

        assertEquals(
            "tools/kws/keywords_raw.txt and stt/WakePhrases.kt must list the same phrases " +
                "(see tools/kws/README.md)",
            model,
            app,
        )
    }

    @Test
    fun noPhraseStartsTwoCommands() {
        val keywords = CameraCommand.entries.flatMap { it.phrases }.map(WakePhrases::keyword)

        assertEquals(keywords.size, keywords.toSet().size)
    }

    /** The phrases in the keyword model's plain-English list, without their ":2.0 #0.25" tuning. */
    private fun keywordModelPhrases(): Set<String> {
        // Unit tests run from the app module, frontend/visor/app.
        val raw = File("../../../tools/kws/keywords_raw.txt")
        assertTrue("Can't find ${raw.absolutePath}", raw.isFile)
        return raw.readLines()
            .map { it.substringBefore(':').substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map(WakePhrases::keyword)
            .toSet()
    }
}
