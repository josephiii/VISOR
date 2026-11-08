package ucf.visor.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Online and offline as they settle: a short drop (Wi-Fi to mobile data, the
 * edge of a network) must not announce "You're offline" or lock buttons.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivitySettledTest {

    /** Each pair is (online, how long it lasts in ms), as the phone reports them. */
    private fun reports(vararg states: Pair<Boolean, Long>) = flow {
        states.forEach { (online, lasts) ->
            emit(online)
            delay(lasts)
        }
    }

    @Test
    fun aShortDropIsNotPassedOn() = runTest {
        val seen = reports(true to 5_000, false to 1_500, true to 5_000).settled().toList()
        assertEquals(listOf(true), seen)
    }

    @Test
    fun goingOfflineCountsOnceItLasts() = runTest {
        val seen = reports(true to 5_000, false to 10_000).settled().toList()
        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun goingOfflineIsPassedOnAfterTheGracePeriod() = runTest {
        var offlineAt = -1L
        reports(true to 2_000, false to 10_000).settled().collect { online ->
            if (!online) offlineAt = currentTime
        }
        assertEquals(2_000 + OFFLINE_GRACE_MS, offlineAt)
    }

    @Test
    fun comingBackOnlineIsQuickerThanGoingOffline() {
        assertTrue(ONLINE_SETTLE_MS < OFFLINE_GRACE_MS)
    }

    @Test
    fun theSameStateTwiceIsPassedOnOnce() = runTest {
        val seen = reports(false to 5_000, false to 5_000, true to 5_000, true to 5_000).settled().toList()
        assertEquals(listOf(false, true), seen)
    }
}
