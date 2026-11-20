package ucf.visor.stt

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Logs which microphone each recording on the phone really uses, for field
 * tests with the glasses (filter logcat by `VisorMicCheck`). Nothing is said
 * or shown to the wearer: it changes nothing VISOR does.
 *
 * [MicRoute] points VISOR's own wake-phrase listener at the glasses directly.
 * "VISOR GO" commands and spoken scene questions are heard by Android's speech
 * recognizer instead, which runs in another app, so VISOR can't point it
 * anywhere: on Android 12 and later it should follow the glasses while they
 * are the communication device, but only a log like this one shows whether it
 * does. A recording that the system has silenced (another app, such as Meta AI
 * listening for "Hey Meta", has the microphone) is logged too.
 *
 * [route] is the microphone VISOR means to hear through, [wakeSessionId] the
 * wake-phrase listener's audio session (to tell its recording apart),
 * [commandListening] whether VISOR has just opened the speech recognizer, and
 * [glassesName] the glasses MWDAT reports.
 */
class MicCheck(
    context: Context,
    private val route: () -> MicRoute.Source,
    private val wakeSessionId: () -> Int?,
    private val commandListening: () -> Boolean,
    private val glassesName: () -> String?,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var lastLogged: List<MicReport.Line>? = null

    private val callback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) = report(configs)
    }

    init {
        audio.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
    }

    /** Logs the recordings running right now, for when VISOR's own route has just changed. */
    fun reportNow() = report(audio.activeRecordingConfigurations)

    private fun report(configs: List<AudioRecordingConfiguration>) {
        val lines = MicReport.lines(
            recordings = configs.map { it.toRecording() },
            route = route(),
            wakeSessionId = wakeSessionId(),
            commandListening = commandListening(),
            glassesName = glassesName(),
        )
        // The callback also fires for changes that don't matter here (formats, effects).
        if (lines == lastLogged) return
        lastLogged = lines
        for (line in lines) {
            if (line.unexpected) Log.w(TAG, line.text) else Log.i(TAG, line.text)
        }
    }

    private fun AudioRecordingConfiguration.toRecording() = MicReport.Recording(
        sessionId = clientAudioSessionId,
        source = clientAudioSource,
        deviceType = audioDevice?.type,
        deviceName = audioDevice?.productName?.toString(),
        silenced = isClientSilenced,
    )

    private companion object {
        const val TAG = "VisorMicCheck"
    }
}

/**
 * What [MicCheck] logs, worked out from the recordings Android reports. Pure,
 * for unit tests (MicReportTest).
 */
object MicReport {

    /** One recording, as Android reports it (another app's is anonymized, but keeps these). */
    data class Recording(
        val sessionId: Int,
        /** A `MediaRecorder.AudioSource` value. */
        val source: Int,
        /** An `AudioDeviceInfo` type, or null when Android doesn't say. */
        val deviceType: Int?,
        val deviceName: String?,
        /** Android has silenced it: another app has the microphone. */
        val silenced: Boolean,
    )

    enum class Who(val label: String) {
        WAKE_LISTENER("Wake-phrase listener"),
        SPEECH_RECOGNIZER("Speech recognizer (VISOR GO commands, spoken questions)"),
        OTHER("Another recording"),
    }

    enum class Mic(val label: String) {
        GLASSES("the glasses' microphone"),
        OTHER_BLUETOOTH("another Bluetooth headset's microphone"),
        PHONE("the phone's microphone"),
        WIRED("a wired or USB headset's microphone"),
        UNKNOWN("a microphone Android didn't name"),
    }

    data class Line(val text: String, val unexpected: Boolean)

    fun who(r: Recording, wakeSessionId: Int?, commandListening: Boolean): Who = when {
        wakeSessionId != null && r.sessionId == wakeSessionId -> Who.WAKE_LISTENER
        r.source == MediaRecorder.AudioSource.VOICE_RECOGNITION -> Who.SPEECH_RECOGNIZER
        // Some recognizers record from the plain microphone source.
        commandListening -> Who.SPEECH_RECOGNIZER
        else -> Who.OTHER
    }

    fun mic(r: Recording, glassesName: String?): Mic = when (r.deviceType) {
        null -> Mic.UNKNOWN
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET ->
            if (GlassesNames.matches(r.deviceName, glassesName)) Mic.GLASSES else Mic.OTHER_BLUETOOTH
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> Mic.PHONE
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> Mic.WIRED
        else -> Mic.UNKNOWN
    }

    fun lines(
        recordings: List<Recording>,
        route: MicRoute.Source,
        wakeSessionId: Int?,
        commandListening: Boolean,
        glassesName: String?,
    ): List<Line> {
        val meant = if (route == MicRoute.Source.GLASSES) "the glasses" else "the phone"
        if (recordings.isEmpty()) return listOf(Line("Nothing is recording (VISOR means to hear through $meant)", false))
        return recordings.map { r ->
            val who = who(r, wakeSessionId, commandListening)
            val mic = mic(r, glassesName)
            val visors = who != Who.OTHER
            // VISOR meant the glasses, and one of its recordings isn't on them.
            val offGlasses = visors && route == MicRoute.Source.GLASSES && mic != Mic.GLASSES
            val text = buildString {
                append("${who.label} hears through ${mic.label}")
                r.deviceName?.let { append(" ($it)") }
                if (visors) append("; VISOR means to hear through $meant")
                if (offGlasses) append(": NOT the glasses")
                if (r.silenced) append("; SILENCED, another app has the microphone")
            }
            Line(text, unexpected = offGlasses || (visors && r.silenced))
        }
    }
}
