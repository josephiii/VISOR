package ucf.visor.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import ucf.visor.stt.CameraCommand

/** Which camera features work offline: see documentation/research/phase1-offline-capabilities.md. */
class AvailabilityTest {

    @Test
    fun onlineEveryFeatureIsAvailable() {
        CameraCommand.entries.forEach { command ->
            assertEquals("$command", Availability.AVAILABLE, command.availability(isOnline = true))
        }
    }

    @Test
    fun offlineTheOnDeviceFeaturesStillWork() {
        assertEquals(Availability.AVAILABLE, CameraCommand.READ_TEXT.availability(isOnline = false))
        assertEquals(Availability.AVAILABLE, CameraCommand.IDENTIFY_OBJECTS.availability(isOnline = false))
    }

    @Test
    fun offlineDescribingASceneFallsBackToNamingObjects() {
        assertEquals(Availability.LIMITED, CameraCommand.DESCRIBE_SCENE.availability(isOnline = false))
    }

    @Test
    fun offlineTheServerOnlyFeaturesAreLocked() {
        assertEquals(Availability.LOCKED, CameraCommand.CHECK_HAZARDS.availability(isOnline = false))
        assertEquals(Availability.LOCKED, CameraCommand.ASK_QUESTION.availability(isOnline = false))
    }

    /** R.string.offline_feature_locked names the ones that still work: it must stay true. */
    @Test
    fun theLockedMessageNamesExactlyTheFeaturesThatStillWork() {
        assertEquals(
            listOf(CameraCommand.READ_TEXT, CameraCommand.IDENTIFY_OBJECTS),
            CameraCommand.entries.filter { it.availability(isOnline = false) == Availability.AVAILABLE },
        )
    }
}
