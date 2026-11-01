package ucf.visor.ui.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorUiState
import ucf.visor.ui.voice.VoiceCommand

/**
 * The glasses' display is about 20° across, so it shows at most four buttons
 * at once; the rest go on further pages behind More.
 */
class GlassesNavScreenTest {

    private val loggedIn = VisorUiState(isAuthComplete = true)
    private val routes = listOf("home", "settings", "hardware_pairing", "help", "motion_lab", null)

    @Test
    fun noPageHasMoreThanFourButtonsCountingMore() {
        routes.forEach { route ->
            val screen = glassesNavScreenFor(route, loggedIn, SessionMode.HAZARD)
            val first = screen.page(0)
            repeat(first.count) { index ->
                val page = screen.page(index)
                val shown = page.items.size + if (page.hasMore) 1 else 0
                assertTrue("$route page ${index + 1} shows $shown buttons", shown <= MaxGlassesButtons)
            }
        }
    }

    @Test
    fun everyButtonIsOnSomePageOnce() {
        val screen = glassesNavScreenFor("settings", loggedIn, SessionMode.HAZARD)
        val pages = (0 until screen.page(0).count).map { screen.page(it) }
        assertEquals(screen.items, pages.flatMap { it.items })
    }

    @Test
    fun moreGoesRoundFromTheLastPageToTheFirst() {
        val screen = glassesNavScreenFor("settings", loggedIn, SessionMode.HAZARD)
        val count = screen.page(0).count
        assertTrue("Settings has more buttons than one page holds", count > 1)
        assertEquals(0, screen.page(count).index)
    }

    @Test
    fun aScreenThatFitsHasNoMore() {
        val home = glassesNavScreenFor("home", loggedIn, SessionMode.HAZARD)
        assertTrue(home.items.size <= MaxGlassesButtons)
        assertFalse(home.page(0).hasMore)
    }

    @Test
    fun theFirstButtonEverywhereIsHomesCaptureButtonForTheMode() {
        val labels = mapOf(
            SessionMode.HAZARD to "Check for hazards",
            SessionMode.SCENE to "Describe the scene",
            SessionMode.READER to "Read text",
            SessionMode.IDENTIFY to "Identify objects",
        )
        routes.forEach { route ->
            labels.forEach { (mode, label) ->
                val first = glassesNavScreenFor(route, loggedIn, mode).items.first()
                assertEquals("$route, $mode", VoiceCommand.Capture, first.command)
                assertEquals("$route, $mode", label, first.label)
            }
        }
    }

    @Test
    fun nothingIsOfferedBeforeLoggingIn() {
        val screen = glassesNavScreenFor("login", VisorUiState(isAuthComplete = false), SessionMode.HAZARD)
        assertTrue(screen.items.isEmpty())
    }
}
