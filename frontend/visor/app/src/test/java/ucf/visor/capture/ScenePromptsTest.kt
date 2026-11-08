package ucf.visor.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.data.Verbosity

/** The Detail setting (Brief, Standard, Detailed) changes what VISOR asks for. */
class ScenePromptsTest {

    @Test
    fun eachDetailSettingAsksForSomethingDifferent() {
        val prompts = Verbosity.entries.map(ScenePrompts::describe)
        assertEquals(Verbosity.entries.size, prompts.toSet().size)

        val questions = Verbosity.entries.map { ScenePrompts.question("Is the light on?", it) }
        assertEquals(Verbosity.entries.size, questions.toSet().size)
    }

    @Test
    fun briefAsksForOneSentenceAndDetailedForColorAndText() {
        assertTrue(ScenePrompts.describe(Verbosity.BRIEF).contains("one short sentence"))
        val detailed = ScenePrompts.describe(Verbosity.DETAILED)
        assertTrue(detailed.contains("colors"))
        assertTrue(detailed.contains("text"))
    }

    @Test
    fun everyPromptIsForLowVisionAndSaysWhereThingsAre() {
        val all = Verbosity.entries.flatMap {
            listOf(ScenePrompts.describe(it), ScenePrompts.question("Where is my cup?", it))
        }
        all.forEach { prompt ->
            assertTrue(prompt, prompt.contains("low vision"))
            assertFalse(prompt, prompt.contains("blind"))
            assertTrue(prompt, prompt.contains("on their left, ahead, or on their right"))
        }
    }

    @Test
    fun theQuestionIsPassedOnWordForWord() {
        assertTrue(ScenePrompts.question("Is the light on?", Verbosity.STANDARD).contains("\"Is the light on?\""))
    }
}
