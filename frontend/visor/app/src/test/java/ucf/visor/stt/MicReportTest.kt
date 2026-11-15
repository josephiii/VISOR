package ucf.visor.stt

import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What MicCheck logs about each recording: whose it is, which microphone it
 * really uses, and whether that is not what VISOR meant. Field tests read
 * these lines to see whether commands are heard through the glasses.
 */
class MicReportTest {

    private val glasses = "Ray-Ban Meta"

    private fun recording(
        sessionId: Int = 1,
        source: Int = MediaRecorder.AudioSource.MIC,
        deviceType: Int? = AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        deviceName: String? = "Ray-Ban Meta 0A1B",
        silenced: Boolean = false,
    ) = MicReport.Recording(sessionId, source, deviceType, deviceName, silenced)

    private fun lines(
        vararg recordings: MicReport.Recording,
        route: MicRoute.Source = MicRoute.Source.GLASSES,
        wakeSessionId: Int? = 1,
        commandListening: Boolean = false,
    ) = MicReport.lines(recordings.toList(), route, wakeSessionId, commandListening, glasses)

    @Test
    fun theWakeListenerIsKnownByItsSession() {
        assertEquals(MicReport.Who.WAKE_LISTENER, MicReport.who(recording(sessionId = 7), 7, commandListening = false))
        assertEquals(MicReport.Who.OTHER, MicReport.who(recording(sessionId = 8), 7, commandListening = false))
    }

    @Test
    fun theSpeechRecognizerIsKnownByItsSourceOrByVisorHavingJustOpenedIt() {
        val recognition = recording(sessionId = 9, source = MediaRecorder.AudioSource.VOICE_RECOGNITION)
        assertEquals(MicReport.Who.SPEECH_RECOGNIZER, MicReport.who(recognition, 1, commandListening = false))
        assertEquals(MicReport.Who.SPEECH_RECOGNIZER, MicReport.who(recording(sessionId = 9), 1, commandListening = true))
    }

    @Test
    fun theGlassesAreTheBluetoothMicNamedLikeThem() {
        assertEquals(MicReport.Mic.GLASSES, MicReport.mic(recording(), glasses))
        assertEquals(MicReport.Mic.OTHER_BLUETOOTH, MicReport.mic(recording(deviceName = "Jabra Evolve"), glasses))
        assertEquals(MicReport.Mic.PHONE, MicReport.mic(recording(deviceType = AudioDeviceInfo.TYPE_BUILTIN_MIC), glasses))
        assertEquals(MicReport.Mic.UNKNOWN, MicReport.mic(recording(deviceType = null), glasses))
    }

    @Test
    fun hearingThroughTheGlassesAsMeantIsNotFlagged() {
        val line = lines(recording()).single()
        assertFalse(line.unexpected)
        assertTrue(line.text, line.text.startsWith("Wake-phrase listener hears through the glasses' microphone"))
    }

    @Test
    fun aCommandHeardThroughThePhoneWhileVisorMeantTheGlassesIsFlagged() {
        val onPhone = recording(
            sessionId = 9,
            source = MediaRecorder.AudioSource.VOICE_RECOGNITION,
            deviceType = AudioDeviceInfo.TYPE_BUILTIN_MIC,
            deviceName = null,
        )
        val line = lines(onPhone).single()
        assertTrue(line.unexpected)
        assertTrue(line.text, line.text.contains("NOT the glasses"))
    }

    @Test
    fun thePhoneMicIsFineWhenVisorMeantThePhone() {
        val onPhone = recording(deviceType = AudioDeviceInfo.TYPE_BUILTIN_MIC, deviceName = null)
        assertFalse(lines(onPhone, route = MicRoute.Source.PHONE).single().unexpected)
    }

    @Test
    fun anotherAppsRecordingIsLoggedButNeverFlagged() {
        val theirs = recording(sessionId = 42, deviceType = AudioDeviceInfo.TYPE_BUILTIN_MIC, silenced = true)
        assertFalse(lines(theirs).single().unexpected)
    }

    @Test
    fun aSilencedVisorRecordingIsFlagged() {
        val line = lines(recording(silenced = true)).single()
        assertTrue(line.unexpected)
        assertTrue(line.text, line.text.contains("SILENCED"))
    }

    @Test
    fun nothingRecordingSaysSo() {
        val line = lines(route = MicRoute.Source.PHONE).single()
        assertFalse(line.unexpected)
        assertEquals("Nothing is recording (VISOR means to hear through the phone)", line.text)
    }
}
