package ucf.visor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sign-up to setup: when a login goes through setup first (ProfileStore.needsSetup),
 * and what a new account's setup starts from (withoutPersonalAnswers).
 */
class NewAccountSetupTest {

    @Test
    fun `a phone with nothing saved goes through setup`() {
        assertTrue(setupNeeded(hasProfile = false, setupPending = false))
    }

    @Test
    fun `a set-up phone goes straight home`() {
        assertFalse(setupNeeded(hasProfile = true, setupPending = false))
    }

    // The bug: a profile saved by an earlier user, or by one setting changed
    // by voice, made a sign-up whose setup was left part way look set up.
    @Test
    fun `a sign-up whose setup was left unfinished goes through setup, even with a profile saved`() {
        assertTrue(setupNeeded(hasProfile = true, setupPending = true))
    }

    private val lastUser = UserProfile(
        userId = "previous-user",
        displayName = "Sam",
        visionTypes = setOf(VisionType.CENTRAL_LOSS, VisionType.CONTRAST_LIGHT),
        visionDescription = "Macular degeneration in both eyes",
        shareVisionDescription = false,
        severity = Severity.SEVERE,
        // How VISOR looks and sounds on this phone.
        speechRate = SpeechRates.Fast,
        verbosity = Verbosity.entries.last(),
        appHighContrast = true,
        themeMode = ThemeMode.DARK,
        textScale = TextScale.LARGE,
        voiceNavigationEnabled = false,
        vibrationEnabled = false,
    )

    @Test
    fun `a new account's setup does not show the last user's answers about themselves`() {
        val fresh = lastUser.withoutPersonalAnswers()
        val blank = UserProfile()

        assertEquals(blank.userId, fresh.userId)
        assertEquals(blank.displayName, fresh.displayName)
        assertEquals(blank.visionTypes, fresh.visionTypes)
        assertEquals(blank.visionDescription, fresh.visionDescription)
        assertEquals(blank.shareVisionDescription, fresh.shareVisionDescription)
        assertEquals(blank.severity, fresh.severity)
    }

    // Someone who turned on high contrast or bigger text to be able to sign
    // up must not lose it the moment setup opens.
    @Test
    fun `a new account keeps how VISOR looks, sounds and listens on this phone`() {
        val fresh = lastUser.withoutPersonalAnswers()

        assertEquals(
            lastUser.copy(
                userId = fresh.userId,
                displayName = fresh.displayName,
                visionTypes = fresh.visionTypes,
                visionDescription = fresh.visionDescription,
                shareVisionDescription = fresh.shareVisionDescription,
                severity = fresh.severity,
            ),
            fresh,
        )
    }
}
