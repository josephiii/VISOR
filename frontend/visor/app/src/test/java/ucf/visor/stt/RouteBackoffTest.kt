package ucf.visor.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When VISOR takes the glasses' microphone back after losing it. Taking it
 * straight back made VISOR and the glasses fight over the Bluetooth call link,
 * with a chime each time; now it waits, and gives up for a while if it keeps
 * dropping.
 */
class RouteBackoffTest {

    private val backoff = RouteBackoff(regrabDelayMs = 5_000, maxDrops = 3, windowMs = 60_000, giveUpMs = 300_000)

    @Test
    fun withNoDropsItMayTakeTheRouteAtOnce() {
        assertEquals(0L, backoff.waitMs(1_000))
    }

    @Test
    fun afterADropItWaitsBeforeTakingItBack() {
        assertFalse(backoff.onDropped(10_000))
        assertEquals(5_000L, backoff.waitMs(10_000))
        assertEquals(2_000L, backoff.waitMs(13_000))
        assertEquals(0L, backoff.waitMs(15_000))
    }

    @Test
    fun threeDropsInAMinuteGiveUpForFiveMinutes() {
        assertFalse(backoff.onDropped(0))
        assertFalse(backoff.onDropped(20_000))
        assertTrue(backoff.onDropped(40_000))
        assertEquals(300_000L, backoff.waitMs(40_000))
        assertEquals(0L, backoff.waitMs(340_000))
    }

    @Test
    fun dropsFarApartNeverGiveUp() {
        assertFalse(backoff.onDropped(0))
        assertFalse(backoff.onDropped(70_000))
        assertFalse(backoff.onDropped(140_000))
        assertEquals(5_000L, backoff.waitMs(140_000))
    }

    @Test
    fun afterGivingUpItCountsAfresh() {
        backoff.onDropped(0)
        backoff.onDropped(1_000)
        assertTrue(backoff.onDropped(2_000))
        assertFalse(backoff.onDropped(400_000))
    }

    /** Given up for the camera: off the glasses' microphone for as long as asked. */
    @Test
    fun blockingKeepsItOffForAsLongAsAsked() {
        backoff.blockFor(0, 600_000)
        assertEquals(600_000L, backoff.waitMs(0))
        assertEquals(0L, backoff.waitMs(600_000))
    }

    /** Turning "Listen through my glasses" back on tries again at once. */
    @Test
    fun resetTriesAgainAtOnce() {
        backoff.onDropped(0)
        backoff.onDropped(1_000)
        backoff.onDropped(2_000)
        backoff.reset()
        assertEquals(0L, backoff.waitMs(3_000))
        assertFalse(backoff.onDropped(3_000))
    }
}
