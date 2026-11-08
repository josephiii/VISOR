package ucf.visor.ui.glasses

import org.junit.Assert.assertEquals
import com.meta.wearable.dat.display.views.IconName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.stt.CameraCommand
import ucf.visor.tts.ReadingState
import ucf.visor.ui.viewmodel.VisorUiState
import ucf.visor.ui.voice.VoiceCommand

/**
 * The glasses' display is about 20° across, so it shows at most four buttons
 * at once; the rest go on further pages behind More. On Home, the four are
 * Home's camera buttons.
 */
class GlassesNavScreenTest {

    private val loggedIn = VisorUiState(isAuthComplete = true)
    private val routes = listOf("home", "settings", "hardware_pairing", "help", "motion_lab", null)

    @Test
    fun noPageHasMoreThanFourButtonsCountingMore() {
        routes.forEach { route ->
            val screen = glassesNavScreenFor(route, loggedIn)
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
        val screen = glassesNavScreenFor("motion_lab", loggedIn)
        val pages = (0 until screen.page(0).count).map { screen.page(it) }
        assertEquals(screen.items, pages.flatMap { it.items })
    }

    @Test
    fun moreGoesRoundFromTheLastPageToTheFirst() {
        val screen = glassesNavScreenFor("motion_lab", loggedIn)
        val count = screen.page(0).count
        assertTrue("the lab has more buttons than one page holds", count > 1)
        assertEquals(0, screen.page(count).index)
    }

    @Test
    fun homeShowsEveryCameraButtonButAskAQuestionInHomesOrderOnOnePage() {
        val home = glassesNavScreenFor("home", loggedIn)

        assertEquals(
            (CameraCommand.entries - CameraCommand.ASK_QUESTION).map { VoiceCommand.RunCamera(it) },
            home.items.map { it.command },
        )
        assertFalse("all four fit with no More", home.page(0).hasMore)
    }

    @Test
    fun cameraButtonsAreNamedAsOnThePhone() {
        assertEquals(
            listOf("Check for hazards", "Read text", "Identify objects", "Describe the scene"),
            glassesNavScreenFor("home", loggedIn).items.map { it.label },
        )
    }

    @Test
    fun everyOtherScreenLeadsHomeFirst() {
        routes.filter { it != "home" }.forEach { route ->
            val items = glassesNavScreenFor(route, loggedIn).items
            assertEquals("$route", VoiceCommand.GoHome, items.first().command)
            assertTrue("$route has no camera buttons", items.none { it.command is VoiceCommand.RunCamera })
        }
    }

    @Test
    fun nothingIsOfferedBeforeLoggingIn() {
        val screen = glassesNavScreenFor("login", VisorUiState(isAuthComplete = false))
        assertTrue(screen.items.isEmpty())
    }

    private val offline = loggedIn.copy(isOnline = false)

    @Test
    fun offlineHomeKeepsEveryButtonInItsPlace() {
        val online = glassesNavScreenFor("home", loggedIn)
        val off = glassesNavScreenFor("home", offline)
        assertEquals(online.items.map { it.command }, off.items.map { it.command })
        assertEquals(online.items.map { it.label }, off.items.map { it.label })
    }

    @Test
    fun offlineOnlyTheFeaturesThatNeedTheInternetShowAPadlock() {
        val locked = glassesNavScreenFor("home", offline).items
            .filter { it.icon == IconName.PADLOCK_CLOSED }
            .map { (it.command as VoiceCommand.RunCamera).command }
        assertEquals(listOf(CameraCommand.CHECK_HAZARDS), locked)
        assertTrue(glassesNavScreenFor("home", loggedIn).items.none { it.icon == IconName.PADLOCK_CLOSED })
    }

    @Test
    fun offlineEveryScreenSaysSo() {
        routes.forEach { route ->
            assertEquals("$route", OFFLINE_SUBTITLE, glassesNavScreenFor(route, offline).subtitle)
            assertNull("$route", glassesNavScreenFor(route, loggedIn).subtitle)
        }
    }

    @Test
    fun whileReadingPauseAndStopComeFirstWithEverythingElseStillReachable() {
        val home = glassesNavScreenFor("home", loggedIn)
        val reading = glassesNavScreenFor("home", loggedIn, ReadingState.READING)

        assertEquals(
            listOf(VoiceCommand.PauseReading, VoiceCommand.StopReading),
            reading.page(0).items.take(2).map { it.command },
        )
        assertEquals(home.items, reading.items.drop(2))
    }

    @Test
    fun pausedOffersResumeInPausesPlace() {
        val paused = glassesNavScreenFor("settings", loggedIn, ReadingState.PAUSED)

        assertEquals(VoiceCommand.ResumeReading, paused.items[0].command)
        assertEquals(VoiceCommand.StopReading, paused.items[1].command)
        assertEquals("Paused", paused.title)
    }

    @Test
    fun anAreYouSureIsAnsweredBeforeTheReadingControls() {
        val asking = loggedIn.copy(isLogoutConfirmVisible = true)
        assertEquals(glassesNavScreenFor("settings", asking), glassesNavScreenFor("settings", asking, ReadingState.READING))
    }
}
