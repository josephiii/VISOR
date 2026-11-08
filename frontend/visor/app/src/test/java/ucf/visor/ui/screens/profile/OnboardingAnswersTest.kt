package ucf.visor.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.data.Severity
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionType

/** How setup hears a spoken answer: a mishearing changes settings out of sight. */
class OnboardingAnswersTest {

    @Test
    fun skipIsHeardAsSkipNotAsAnAnswer() {
        listOf("skip", "Skip.", "skip for now", "Next", "skip this one").forEach {
            assertTrue(it, OnboardingAnswers.isSkip(it))
        }
    }

    @Test
    fun anAnswerThatOnlyStartsLikeSkipIsKept() {
        assertFalse(OnboardingAnswers.isSkip("next to nothing in my left eye"))
        assertFalse(OnboardingAnswers.isSkip("Skipper"))
    }

    @Test
    fun aNameLosesWhatWasSaidBeforeIt() {
        assertEquals("Sam", OnboardingAnswers.name("Sam"))
        assertEquals("Sam", OnboardingAnswers.name("Call me Sam."))
        assertEquals("Maria Lopez", OnboardingAnswers.name("my name is Maria Lopez"))
        assertEquals("Ian", OnboardingAnswers.name("Ian"))
        assertNull(OnboardingAnswers.name("  "))
    }

    @Test
    fun yesAndNoAreWholeWords() {
        assertEquals(true, OnboardingAnswers.yesNo("yes"))
        assertEquals(true, OnboardingAnswers.yesNo("Yes, I know"))
        assertEquals(true, OnboardingAnswers.yesNo("keep it on"))
        assertEquals(false, OnboardingAnswers.yesNo("no"))
        assertEquals(false, OnboardingAnswers.yesNo("No, turn it off"))
        assertEquals(false, OnboardingAnswers.yesNo("no, keep it off"))
        assertNull(OnboardingAnswers.yesNo("I don't know"))
        assertNull(OnboardingAnswers.yesNo("now what"))
    }

    @Test
    fun severalKindsOfVisionLossCanBeNamedAtOnce() {
        assertEquals(
            setOf(VisionType.CENTRAL_LOSS, VisionType.BLUR_LOW_ACUITY),
            OnboardingAnswers.visionTypes("center loss and it's blurry"),
        )
        assertEquals(setOf(VisionType.PERIPHERAL_LOSS), OnboardingAnswers.visionTypes("side loss"))
        assertEquals(setOf(VisionType.CONTRAST_LIGHT), OnboardingAnswers.visionTypes("light sensitivity"))
    }

    @Test
    fun notSureCountsOnlyAlone() {
        assertEquals(setOf(VisionType.NOT_SURE), OnboardingAnswers.visionTypes("I'm not sure"))
        assertEquals(setOf(VisionType.CENTRAL_LOSS), OnboardingAnswers.visionTypes("center, but I'm not sure"))
        assertNull(OnboardingAnswers.visionTypes("hmm"))
    }

    @Test
    fun theOtherQuestionsStillUnderstandTheirOptions() {
        assertEquals(Severity.MILD, OnboardingAnswers.severity("a little"))
        assertEquals(Severity.SEVERE, OnboardingAnswers.severity("a lot"))
        assertEquals(Verbosity.DETAILED, OnboardingAnswers.verbosity("detailed please"))
        assertTrue(OnboardingAnswers.isStart("Start"))
        assertFalse(OnboardingAnswers.isStart("wait"))
    }
}
