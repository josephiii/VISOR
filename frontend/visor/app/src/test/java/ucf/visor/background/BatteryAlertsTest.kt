package ucf.visor.background

import org.junit.Assert.assertEquals
import org.junit.Test

/** The glasses' battery, said aloud as it runs low with the screen off: once per threshold. */
class BatteryAlertsTest {

    /** Feeds [levels] in order (charging = false) and returns what was said at each. */
    private fun said(vararg levels: Int?): List<Int?> {
        var last: Int? = null
        return levels.map { level ->
            val (now, next) = BatteryAlerts.next(last, level, charging = false)
            last = next
            now
        }
    }

    @Test
    fun eachThresholdIsSaidOnceOnTheWayDown() {
        assertEquals(listOf(null, 20, null, null, 10, null), said(25, 20, 19, 15, 10, 8))
    }

    @Test
    fun aBigDropSaysOnlyTheLowestThreshold() {
        assertEquals(listOf(null, 10, null), said(50, 8, 7))
    }

    @Test
    fun unknownBatterySaysNothing() {
        assertEquals(listOf(null, null), said(null, null))
    }

    @Test
    fun aSmallWobbleIsNotSaidAgain() {
        assertEquals(listOf(20, null, null), said(20, 21, 20))
    }

    @Test
    fun afterClimbingWellAboveItIsSaidAgain() {
        assertEquals(listOf(20, null, 20), said(20, 26, 20))
    }

    @Test
    fun chargingResets() {
        var (now, last) = BatteryAlerts.next(null, 20, charging = false)
        assertEquals(20, now)
        val charged = BatteryAlerts.next(last, 20, charging = true)
        assertEquals(null, charged.first)
        last = charged.second
        // Unplugged again at the same level: said again, it is news.
        now = BatteryAlerts.next(last, 20, charging = false).first
        assertEquals(20, now)
    }

    @Test
    fun itIsSaidWithTheActualLevel() {
        assertEquals("Your glasses battery is low, at 8 percent.", BatteryAlerts.spoken(8))
    }
}
