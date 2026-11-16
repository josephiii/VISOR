package ucf.visor.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An answer never opens by reading the user's own description back to them,
 * and nothing about the scene is ever dropped while taking it out.
 */
class VisionEchoTest {

    private val glaucoma = "I have glaucoma, sun washes everything out"
    private val scene = "Ahead is a dark TV screen maybe 3 feet away."

    @Test
    fun theirWordsAtTheStartAreTakenOut() {
        // The example from the bug report, word for word.
        assertEquals(scene, VisionEcho.strip("$glaucoma. $scene", glaucoma))
    }

    @Test
    fun theirWordsSaidBackAsYouAreTakenOut() {
        assertEquals(scene, VisionEcho.strip("You have glaucoma; sun washes everything out. $scene", glaucoma))
        assertEquals(scene, VisionEcho.strip("They have glaucoma and sun washes everything out. $scene", glaucoma))
    }

    @Test
    fun aFramingPhraseAndQuotesGoWithThem() {
        assertEquals(scene, VisionEcho.strip("In your own words, \"$glaucoma.\" $scene", glaucoma))
        assertEquals(scene, VisionEcho.strip("You said that you have glaucoma, sun washes everything out. $scene", glaucoma))
    }

    @Test
    fun theSceneInTheSameSentenceIsKept() {
        assertEquals(
            "Ahead is a dark TV screen.",
            VisionEcho.strip("$glaucoma, and ahead is a dark TV screen.", glaucoma),
        )
    }

    @Test
    fun curlyQuotesAndApostrophesStillMatch() {
        assertEquals(
            "There is a letter on the table ahead.",
            VisionEcho.strip("“You can’t read your mail.” There is a letter on the table ahead.", "I can't read my mail"),
        )
    }

    @Test
    fun repeatingTheFirstPartOfALongDescriptionCounts() {
        val long = "I have glaucoma, sun washes everything out. I also can't read small print on labels."
        assertEquals(scene, VisionEcho.strip("$glaucoma. $scene", long))
    }

    @Test
    fun aDescriptionThatSharesAFewWordsIsLeftAlone() {
        val answer = "There is mail on the table ahead; you can't read it from here."
        assertEquals(answer, VisionEcho.strip(answer, "I can't read my mail"))
        val sunny = "Sun washes over the sidewalk ahead, and a bench is on your left."
        assertEquals(sunny, VisionEcho.strip(sunny, glaucoma))
    }

    @Test
    fun anEchoLaterInTheAnswerIsLeftAlone() {
        val answer = "$scene $glaucoma."
        assertEquals(answer, VisionEcho.strip(answer, glaucoma))
    }

    @Test
    fun shortWordsAreTakenOutOnlyAsAWholeClause() {
        assertEquals("Ahead is a door.", VisionEcho.strip("Tunnel vision. Ahead is a door.", "Tunnel vision"))
        val answer = "Tunnel vision makes the hallway ahead look narrow."
        assertEquals(answer, VisionEcho.strip(answer, "Tunnel vision"))
        // One word is too little to tell an echo from a description.
        assertEquals("Glaucoma. $scene", VisionEcho.strip("Glaucoma. $scene", "Glaucoma"))
    }

    @Test
    fun anAnswerThatIsOnlyTheEchoBecomesEmpty() {
        // Empty, so SceneDescriber says it couldn't describe the scene instead.
        assertEquals("", VisionEcho.strip("$glaucoma.", glaucoma))
    }

    @Test
    fun withNoDescriptionTheAnswerIsUnchanged() {
        val answer = "$glaucoma. $scene"
        assertEquals(answer, VisionEcho.strip(answer, null))
        assertEquals(answer, VisionEcho.strip(answer, "   "))
    }
}
