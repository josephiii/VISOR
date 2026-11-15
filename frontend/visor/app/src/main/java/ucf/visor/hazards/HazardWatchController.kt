package ucf.visor.hazards

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ucf.visor.capture.PhotoSource
import ucf.visor.data.CameraChoice
import ucf.visor.data.Severity
import ucf.visor.data.SoundCategory
import ucf.visor.data.VisionType
import ucf.visor.data.VoiceCategory
import ucf.visor.data.Volumes
import ucf.visor.engine.VisorState
import ucf.visor.network.HazardClient
import ucf.visor.tts.Chimes
import ucf.visor.tts.Chimes.Chime
import ucf.visor.tts.Speaker
import ucf.visor.tts.SpokenPhrases
import ucf.visor.ui.feedback.Haptics
import kotlin.coroutines.resume

/**
 * The hazard watch, wired into VISOR: VisorEngine owns one and turns it on and
 * off as [VisorState.hazardWatchOn] says (Home's switch, or "start hazard
 * watch"). It takes the pictures ([camera], the chosen camera, so the glasses'
 * held session), asks the server, and says the alerts with their tones and
 * buzzes. What to say, and when, is [HazardWatch]'s and [AlertPolicy]'s.
 */
class HazardWatchController(
    private val app: Context,
    scope: CoroutineScope,
    private val state: VisorState,
    private val camera: PhotoSource,
    private val client: HazardClient,
    private val speaker: Speaker,
    private val chimes: Chimes,
    private val haptics: Haptics,
    private val isOnline: () -> Boolean,
) {
    /** Why the last check failed, in words, for "can't see". */
    @Volatile
    private var lastProblem: String? = null

    private val watch = HazardWatch(
        scope = scope,
        check = ::check,
        onAlert = ::sayAlert,
        onCantSee = {
            haptics.rejected()
            say(SpokenPhrases.hazardWatchCantSee(lastProblem))
        },
        onBack = { say(SpokenPhrases.HAZARD_WATCH_BACK) },
        now = SystemClock::elapsedRealtime,
        config = ::config,
    )

    init {
        scope.launch { watch.state.collect(state::setHazardWatchState) }
    }

    val isOn: Boolean get() = watch.isOn

    /**
     * Starts the watch, says so, and keeps VISOR running with the screen off.
     * [quiet] after VISOR restarted itself: it says it is back on instead.
     * Returns false when it can't start (offline), having said why.
     */
    fun start(quiet: Boolean = false): Boolean {
        if (watch.isOn) return true
        if (!isOnline()) {
            haptics.rejected()
            speaker.speak(SpokenPhrases.HAZARD_WATCH_OFFLINE, VoiceCategory.ANSWERS)
            return false
        }
        lastProblem = null
        HazardWatchService.start(app, phoneCamera = state.profile.value.camera == CameraChoice.PHONE)
        watch.start()
        haptics.ready()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when {
            quiet -> say(SpokenPhrases.HAZARD_WATCH_BACK_ON)
            prefs.getBoolean(KEY_INTRODUCED, false) -> say(SpokenPhrases.HAZARD_WATCH_ON)
            else -> {
                // The first time: what it does, and what it can't see.
                say(SpokenPhrases.HAZARD_WATCH_FIRST_TIME)
                prefs.edit().putBoolean(KEY_INTRODUCED, true).apply()
            }
        }
        // A watch that can't be heard shouldn't silently run.
        if (Volumes.isOff(state.profile.value.audio.loudness(SoundCategory.HAZARD_ALERTS))) {
            speaker.speak(SpokenPhrases.HAZARD_ALERTS_MUTED, VoiceCategory.ANSWERS)
        }
        return true
    }

    fun stop() {
        if (!watch.isOn) return
        watch.stop()
        HazardWatchService.stop(app)
        haptics.listeningStopped()
        say(SpokenPhrases.HAZARD_WATCH_OFF)
    }

    /** The camera was switched while watching: the service says which camera it keeps running. */
    fun onCameraChanged(camera: CameraChoice) {
        if (watch.isOn) HazardWatchService.start(app, phoneCamera = camera == CameraChoice.PHONE)
    }

    /** VISOR is going away (its process ending): stop without a word. */
    fun close() {
        watch.stop()
        HazardWatchService.stop(app)
    }

    /**
     * One check: the newest picture from the chosen camera, made smaller for the
     * trip (640 px on its long side), and what the server's watch filter finds
     * in it. Null when either step fails, with why in [lastProblem].
     */
    private suspend fun check(): CheckedFrame? {
        // Timed from when the picture arrives, not when it was asked for: a slow
        // camera start (the phone's) must not make every answer look late.
        var takenMs = SystemClock.elapsedRealtime()
        val photo = suspendCancellableCoroutine<Bitmap?> { done ->
            camera.capturePhoto(
                onPhoto = {
                    takenMs = SystemClock.elapsedRealtime()
                    if (done.isActive) done.resume(it)
                },
                onError = { problem ->
                    lastProblem = problem
                    if (done.isActive) done.resume(null)
                },
            )
        } ?: return null
        val frame = withContext(Dispatchers.Default) {
            val scale = FRAME_PX.toFloat() / maxOf(photo.width, photo.height)
            if (scale >= 1f) photo
            else Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true)
        }
        val hazards = client.detect(frame, watch = true)
        if (hazards == null) {
            lastProblem = SpokenPhrases.HAZARD_WATCH_NO_SERVER
            return null
        }
        lastProblem = null
        return CheckedFrame(hazards, frame.width, frame.height, takenMs)
    }

    /**
     * Fitted to the wearer: side notices for someone who said they have trouble
     * seeing the sides, and more room to react for someone whose vision affects
     * them a lot.
     */
    private fun config(): HazardWatchConfig {
        val profile = state.profile.value
        return HazardWatchConfig(
            includeNotices = VisionType.PERIPHERAL_LOSS in profile.visionTypes,
            closeM = if (profile.severity == Severity.SEVERE) SEVERE_CLOSE_M else HazardWatchConfig().closeM,
        )
    }

    /** A hazard: its tone (toward its side) and buzz first, faster than words, then the words. */
    private fun sayAlert(alert: Alert) {
        when (alert.urgency) {
            Urgency.CRITICAL -> {
                chimes.play(Chime.HAZARD_CRITICAL, pan = alert.side.pan)
                haptics.hazardCritical()
            }
            Urgency.WARNING -> {
                chimes.play(Chime.HAZARD_WARNING, pan = alert.side.pan)
                haptics.hazardWarning()
            }
            Urgency.NOTICE -> Unit
        }
        // "Repeat that" says it again: a warning half heard must be repeatable.
        speaker.speakAt(SpokenPhrases.hazardAlert(alert), loudness(), rememberAs = VoiceCategory.ANSWERS)
    }

    /** What the hazard watch says, at the Hazard alerts volume, over anything else (a reading pauses). */
    private fun say(text: String) = speaker.speakAt(text, loudness())

    private fun loudness() = state.profile.value.audio.loudness(SoundCategory.HAZARD_ALERTS)

    private companion object {
        /** The frames' long side: what YOLO works at, a third of the data. */
        const val FRAME_PX = 640
        const val PREFS = "hazard_watch"
        const val KEY_INTRODUCED = "introduced"

        /** The close band's edge for users whose vision affects them a lot: more time to react. */
        const val SEVERE_CLOSE_M = 3f
    }
}
