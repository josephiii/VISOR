package ucf.visor.wearables

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.wearables.GlassesRecovery.Situation
import ucf.visor.wearables.GlassesRecovery.Step

/**
 * What VISOR does when a glasses picture fails, step by step. Built from the
 * 2026-11-13 field logs and Meta's guidance (#138, #192): camera before
 * microphone, a fresh session when the SDK still sees the glasses, then
 * restarting VISOR, never in a loop.
 */
class GlassesRecoveryTest {

    private val lost = Situation(
        glassesOnPhone = true,
        micOnGlasses = false,
        sdkSeesGlasses = false,
        retried = false,
        screenShown = true,
        sinceLastRestartMs = null,
    )

    @Test
    fun glassesThatAreReallyOffAreNotRecovered() {
        assertEquals(Step.GIVE_UP, GlassesRecovery.next(lost.copy(glassesOnPhone = false)))
        assertEquals(Step.GIVE_UP, GlassesRecovery.next(lost.copy(glassesOnPhone = false, micOnGlasses = true)))
    }

    @Test
    fun theCameraWinsOverTheGlassesMicrophone() {
        assertEquals(Step.YIELD_MIC, GlassesRecovery.next(lost.copy(micOnGlasses = true)))
        assertEquals(Step.YIELD_MIC, GlassesRecovery.next(lost.copy(micOnGlasses = true, sdkSeesGlasses = true)))
    }

    @Test
    fun aLiveLinkGetsOneFreshSession() {
        assertEquals(Step.RETRY, GlassesRecovery.next(lost.copy(sdkSeesGlasses = true)))
        assertEquals(Step.GIVE_UP, GlassesRecovery.next(lost.copy(sdkSeesGlasses = true, retried = true)))
    }

    /** Restarting the SDK in-process does not reconnect on MWDAT 1.0.0; restarting VISOR does. */
    @Test
    fun aLostLinkRestartsVisor() {
        assertEquals(Step.RESTART_VISOR, GlassesRecovery.next(lost))
    }

    @Test
    fun withNoScreenItAsksToOpenVisor() {
        assertEquals(
            Step.ASK_TO_OPEN_VISOR,
            GlassesRecovery.next(lost.copy(screenShown = false)),
        )
    }

    /** A broken pair must not make VISOR restart over and over. */
    @Test
    fun visorRestartsItselfAtMostEveryMinute() {
        val justRestarted = lost.copy(sinceLastRestartMs = 30_000L)
        assertEquals(Step.GIVE_UP, GlassesRecovery.next(justRestarted))
        assertEquals(Step.GIVE_UP, GlassesRecovery.next(justRestarted.copy(screenShown = false)))
        assertEquals(
            Step.RESTART_VISOR,
            GlassesRecovery.next(justRestarted.copy(sinceLastRestartMs = GlassesRecovery.MIN_RESTART_GAP_MS)),
        )
    }

    /** Followed through as VisorEngine does: the microphone first, then a fresh session. */
    @Test
    fun aSessionKilledByTheCallLinkRecoversWithoutRestarting() {
        var s = lost.copy(micOnGlasses = true, sdkSeesGlasses = true)
        assertEquals(Step.YIELD_MIC, GlassesRecovery.next(s))
        s = s.copy(micOnGlasses = false)
        assertEquals(Step.RETRY, GlassesRecovery.next(s))
    }
}
