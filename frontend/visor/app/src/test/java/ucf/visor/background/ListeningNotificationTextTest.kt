package ucf.visor.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.background.ListeningNotificationText.Answer
import ucf.visor.background.ListeningNotificationText.Glasses
import ucf.visor.wearables.GlassesConnectionNotice

/**
 * Background listening's notification, checked against the accessibility rules
 * in ListeningNotificationText: words rather than icons or colour, understood
 * on its own, nothing private on the lock screen, and short.
 */
class ListeningNotificationTextTest {

    private val glasses = Glasses(connected = true, batteryPercent = 40, charging = false, worn = true)

    @Test
    fun theTitleSaysWhichMicrophoneInWords() {
        assertEquals("VISOR is listening through your glasses", build(throughGlasses = true).title)
        assertEquals("VISOR is listening through your phone", build(throughGlasses = false).title)
    }

    @Test
    fun withNoAnswerItSaysWhatToSay() {
        val content = build()
        assertEquals(ListeningNotificationText.HINT, content.text)
        assertNull(content.answeredAtMillis)
    }

    @Test
    fun anAnswerSaysWhichFeatureGaveIt() {
        val content = build(answer = Answer("Read text", "Exit on the left", atMillis = 1_000L))
        assertEquals("Last answer, Read text: Exit on the left", content.text)
        assertTrue(content.bigText.startsWith("Last answer, Read text: Exit on the left"))
        assertEquals(1_000L, content.answeredAtMillis)
    }

    /** It may be text read off someone's mail or bank statement. */
    @Test
    fun theLockScreenNeverShowsTheAnswer() {
        val secret = "Account 1234, balance 500 dollars"
        val content = build(answer = Answer("Read text", secret), glasses = glasses)
        assertFalse(content.publicTitle.contains(secret))
        assertFalse(content.publicText.contains(secret))
        assertFalse(content.publicText.contains("Read text"))
        assertEquals("Glasses battery 40%.", content.publicText)
    }

    @Test
    fun theGlassesBatteryIsInWords() {
        assertEquals("Glasses battery 40%.", ListeningNotificationText.glassesLine(glasses))
        assertEquals("Glasses 40%", build(glasses = glasses).subText)
        assertTrue(build(glasses = glasses).bigText.endsWith("Glasses battery 40%."))
    }

    /** "Low" is written, not only shown in red. */
    @Test
    fun aLowBatterySaysLow() {
        assertEquals(
            "Glasses battery low, 15%.",
            ListeningNotificationText.glassesLine(glasses.copy(batteryPercent = 15)),
        )
        assertEquals(
            "Glasses battery 15%, charging.",
            ListeningNotificationText.glassesLine(glasses.copy(batteryPercent = 15, charging = true)),
        )
    }

    @Test
    fun otherGlassesStatesAreSaid() {
        assertEquals("Glasses not connected.", ListeningNotificationText.glassesLine(glasses.copy(connected = false)))
        assertEquals(
            "Glasses battery 40%, not being worn.",
            ListeningNotificationText.glassesLine(glasses.copy(worn = false)),
        )
        // What the SDK does not know is left out, not read as "unknown".
        assertEquals(
            "Glasses connected.",
            ListeningNotificationText.glassesLine(glasses.copy(batteryPercent = null, worn = null)),
        )
    }

    /** What to do next comes first, on the lock screen too, and the answer is not lost. */
    @Test
    fun aLostLinkSaysToTapAndComesFirst() {
        val content = ListeningNotificationText.build(
            throughGlasses = false,
            answer = Answer("Read text", "Exit on the left"),
            glasses = glasses,
            connection = GlassesConnectionNotice.LOST,
        )
        assertEquals(ListeningNotificationText.LINK_LOST, content.text)
        assertTrue(content.bigText.startsWith(ListeningNotificationText.LINK_LOST))
        assertTrue(content.bigText.contains("Last answer, Read text: Exit on the left"))
        assertEquals(ListeningNotificationText.LINK_LOST, content.publicText)
        assertFalse(content.publicText.contains("Exit on the left"))
    }

    /** Each step of a reconnect is written out, first, so a glance at the shade says what VISOR is doing. */
    @Test
    fun reconnectingAndRestartingAreWrittenOut() {
        val expected = mapOf(
            GlassesConnectionNotice.RECONNECTING to "Reconnecting to your glasses.",
            GlassesConnectionNotice.RESTARTING to "VISOR is restarting to reconnect to your glasses.",
            GlassesConnectionNotice.RECONNECTED to "Your glasses are reconnected.",
        )
        for ((notice, line) in expected) {
            val content = ListeningNotificationText.build(
                throughGlasses = true,
                answer = null,
                glasses = glasses,
                connection = notice,
            )
            assertEquals(line, content.text)
            assertEquals(line, content.publicText)
            assertTrue(content.bigText.endsWith("Glasses battery 40%."))
        }
        assertNull(ListeningNotificationText.connectionLine(GlassesConnectionNotice.NONE))
    }

    @Test
    fun noGlassesNoGlassesLine() {
        val content = build(glasses = null)
        assertNull(content.subText)
        assertEquals(ListeningNotificationText.HINT, content.bigText)
    }

    @Test
    fun aLongAnswerIsCutAtAWordAndSaysWhereTheRestIs() {
        val page = (1..200).joinToString(" ") { "word$it" }
        val shortened = ListeningNotificationText.shorten(page)
        assertTrue(shortened.endsWith(ListeningNotificationText.MORE_IN_VISOR))
        val kept = shortened.removeSuffix("… ${ListeningNotificationText.MORE_IN_VISOR}")
        assertTrue(kept.length <= ListeningNotificationText.MAX_ANSWER_CHARS)
        assertTrue("cut mid-word: $kept", page.startsWith("$kept "))
    }

    @Test
    fun aShortAnswerIsKeptWhole() {
        assertEquals("A cup, a table.", ListeningNotificationText.shorten("  A cup,\n a table. "))
    }

    private fun build(
        throughGlasses: Boolean = true,
        answer: Answer? = null,
        glasses: Glasses? = null,
    ) = ListeningNotificationText.build(throughGlasses, answer, glasses)
}
