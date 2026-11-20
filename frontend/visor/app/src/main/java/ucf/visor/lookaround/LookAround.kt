package ucf.visor.lookaround

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ucf.visor.capture.GlassesPhotoSource
import ucf.visor.motion.HeadMotion
import ucf.visor.motion.HeadingTracker
import ucf.visor.network.Hazard
import ucf.visor.wearables.HeldGlassesSession
import kotlin.coroutines.resume

/**
 * What Home needs of Look around: what it found, a way to be guided to one,
 * and its button's "Tap to stop".
 */
interface LookAroundControls {
    val found: StateFlow<LookAround.Found?>
    fun guideTo(target: Sighting)
    fun stop(): Boolean
}

/**
 * Look around: the wearer turns their head, VISOR takes a picture at each stop
 * of a [ScanPlan] as the head holds still there, finds the objects in each
 * (the backend's hazard detection: what, where in the photo, how far), places
 * each in the room by the head's direction when its photo was taken, and says
 * what is around them by clock hour. Then "guide me to the chair" leads the
 * head round to it by tone.
 *
 * Built natively on MWDAT 1.0: the glasses camera stream and the Motion
 * capability in one glasses session, the pairing the head-motion lab proved
 * (documentation/developer/look-around.md).
 *
 * **One session throughout.** The scan pins the session VISOR holds for the
 * camera ([HeldGlassesSession.pin]), starts the camera stream, then attaches
 * Motion to that same session (the lab's order: a capability added while the
 * device is still arriving fails). The pin, and Motion, stay for
 * [GUIDE_WINDOW_MS] after the answer, so "guide me to" still knows which way
 * the room's directions point. Afterwards both are let go, and the glasses
 * return to what they were doing (tap navigation, on display glasses).
 *
 * Everything runs on [scope], the engine's main-thread scope.
 */
class LookAround(
    private val scope: CoroutineScope,
    private val session: HeldGlassesSession,
    private val camera: GlassesPhotoSource,
    private val motion: HeadMotion,
    private val detect: suspend (Bitmap) -> List<Hazard>?,
    private val tones: GuideTonePlayer,
    private val say: Say,
    /** The plan for this user: wide for trouble seeing the sides. Read at each scan. */
    private val plan: () -> ScanPlan,
    /** How many objects a Brief answer names, or [Int.MAX_VALUE]. Read at each scan. */
    private val limit: () -> Int,
    /** Whether to end the answer with how to ask for guidance. */
    private val guideHint: () -> Boolean,
    private val isSpeaking: () -> Boolean,
    private val events: Events,
) : LookAroundControls {
    /** How Look around speaks: a step of the scan, or an answer. */
    interface Say {
        fun step(text: String)
        fun answer(text: String)
    }

    /** What Home and the Last answer card show of it. */
    interface Events {
        fun onScanStarted()
        fun onScanThinking()

        /** The scan's or the guide's answer, or why it couldn't give one. */
        fun onAnswered(text: String)
        fun onGuideStarted()
    }

    enum class Activity { IDLE, SCANNING, THINKING, GUIDING }

    private val _activity = MutableStateFlow(Activity.IDLE)
    val activity: StateFlow<Activity> = _activity.asStateFlow()

    /** What the last scan found, while it can still be guided to; null otherwise. */
    data class Found(val sightings: List<Sighting>, val facingYawDeg: Double)

    private val _found = MutableStateFlow<Found?>(null)
    override val found: StateFlow<Found?> = _found.asStateFlow()

    private var job: Job? = null
    private var windowJob: Job? = null

    val isBusy: Boolean get() = job?.isActive == true

    /** Starts a scan. Said aloud if one is already running. */
    fun start() {
        if (isBusy) {
            say.step(LookAroundPhrases.ALREADY_LOOKING)
            return
        }
        windowJob?.cancel()
        _found.value = null
        job = scope.launch {
            _activity.value = Activity.SCANNING
            events.onScanStarted()
            try {
                val answer = scan()
                events.onAnswered(answer)
                say.answer(answer)
            } finally {
                tones.stop()
                _activity.value = Activity.IDLE
                // Keep Motion for guidance only when there is something to guide to.
                if (_found.value?.sightings.isNullOrEmpty()) release() else openGuideWindow()
            }
        }
    }

    /** "Guide me to [query]". */
    fun guideTo(query: String) {
        val found = _found.value
        when {
            isBusy -> say.step(LookAroundPhrases.ALREADY_LOOKING)
            found == null || motion.status.value != HeadMotion.Status.RUNNING -> say.answer(LookAroundPhrases.GUIDE_NEEDS_LOOK)
            query.isBlank() -> say.step(LookAroundPhrases.GUIDE_WHAT)
            else -> SceneMap.find(found.sightings, query)
                ?.let(::guideTo)
                ?: say.answer(LookAroundPhrases.notFound(query, found.sightings))
        }
    }

    /** Leads the head to [target], one of [found]'s sightings (Home's "Guide me" buttons). */
    override fun guideTo(target: Sighting) {
        if (isBusy || _found.value == null) return
        openGuideWindow()
        job = scope.launch {
            _activity.value = Activity.GUIDING
            events.onGuideStarted()
            try {
                val answer = guide(target)
                events.onAnswered(answer)
                say.answer(answer)
            } finally {
                tones.stop()
                _activity.value = Activity.IDLE
            }
        }
    }

    /** Stops a scan or guidance in progress ("VISOR GO, stop", or Home's button). Returns whether one was. */
    override fun stop(): Boolean {
        if (!isBusy) return false
        val wasScanning = _activity.value != Activity.GUIDING
        job?.cancel()
        tones.stop()
        events.onAnswered(if (wasScanning) LookAroundPhrases.STOPPED else LookAroundPhrases.GUIDE_STOPPED)
        say.step(if (wasScanning) LookAroundPhrases.STOPPED else LookAroundPhrases.GUIDE_STOPPED)
        return true
    }

    // ---------------------------------------------------------------------

    private class Photo(val yawDeg: Double, val widthPx: Int, val index: Int, val hazards: Deferred<List<Hazard>?>)

    /** One whole scan; returns what to say. */
    private suspend fun scan(): String {
        session.pin(PIN_OWNER)
        camera.prepareStream()?.let { return it }
        startMotion()?.let { return it }
        motion.recenter()

        val sequencer = ScanSequencer(plan())
        val photos = mutableListOf<Photo>()
        val missed = mutableListOf<Double>()
        var facing = 0.0
        // Beeps lead toward the next stop; silence once there says "hold it
        // there", until the glasses' shutter sound. Quiet while VISOR speaks.
        tones.start(scope) {
            val yaw = motion.pose.value?.yawDeg
            val target = sequencer.target
            if (yaw == null || target == null || isSpeaking()) null
            else (target - yaw).takeIf { kotlin.math.abs(it) > ScanSequencer.TOLERANCE_DEG }
        }

        suspend fun handle(actions: List<ScanSequencer.Action>) {
            for (action in actions) when (action) {
                is ScanSequencer.Action.Say -> sayStep(LookAroundPhrases.cue(action.cue))
                is ScanSequencer.Action.Missed -> if (action.stopYawDeg != 0.0) missed += action.stopYawDeg
                is ScanSequencer.Action.Finished -> facing = action.yawDeg
                is ScanSequencer.Action.Capture -> {
                    val bitmap = takePhoto()
                    if (bitmap != null) {
                        Log.i(TAG, "Picture ${photos.size} at ${"%.1f".format(action.yawDeg)}°")
                        // Found while the head moves on to the next stop. A failure is
                        // that picture's side going unchecked, never the scan crashing.
                        val hazards = scope.async {
                            runCatching { detect(bitmap) }
                                .onFailure { Log.w(TAG, "Object detection failed", it) }
                                .getOrNull()
                        }
                        photos += Photo(action.yawDeg, bitmap.width, photos.size, hazards)
                    }
                    handle(sequencer.onCaptureDone(taken = bitmap != null))
                }
            }
        }

        var last: HeadingTracker.Pose? = null
        while (!sequencer.isDone) {
            val pose = nextPose(last) ?: return LookAroundPhrases.LOST_HEAD_MOTION
            last = pose
            handle(sequencer.onPose(pose, SystemClock.elapsedRealtime()))
        }
        tones.stop()

        if (photos.isEmpty()) return LookAroundPhrases.NOT_CHECKED
        _activity.value = Activity.THINKING
        events.onScanThinking()
        val answers = withTimeoutOrNull(DETECT_TIMEOUT_MS) { photos.map { it.hazards }.awaitAll() }
            ?: return LookAroundPhrases.NOT_CHECKED
        if (answers.all { it == null }) return LookAroundPhrases.NOT_CHECKED
        // A picture whose objects couldn't be found leaves its side unchecked.
        photos.zip(answers).filter { (_, hazards) -> hazards == null }.forEach { (photo, _) ->
            if (photo.yawDeg != 0.0) missed += photo.yawDeg
        }
        val sightings = SceneMap.merge(
            photos.zip(answers).flatMap { (photo, hazards) ->
                SceneMap.sightings(hazards.orEmpty(), photo.widthPx, photo.yawDeg, photo.index)
            },
        )
        Log.i(TAG, "Found ${sightings.size} object(s) in ${photos.size} picture(s); facing ${"%.1f".format(facing)}°")
        _found.value = Found(sightings, facing)
        return LookAroundPhrases.found(sightings, facing, limit(), missed, guideHint() && sightings.isNotEmpty())
    }

    /** Leads the head to [target]; returns what to say at the end. */
    private suspend fun guide(target: Sighting): String {
        val sequencer = GuideSequencer(target)
        val start = motion.pose.value ?: return LookAroundPhrases.GUIDE_NEEDS_LOOK
        val opening = LookAroundPhrases.guideStart(target, start.yawDeg)
        if (kotlin.math.abs(target.relativeTo(start.yawDeg)) <= GuideSequencer.ON_TARGET_DEG) return opening
        say.step(opening)
        tones.start(scope) { if (isSpeaking()) null else sequencer.errorDeg }
        var last: HeadingTracker.Pose? = null
        while (!sequencer.isDone) {
            val pose = nextPose(last) ?: return LookAroundPhrases.LOST_HEAD_MOTION
            last = pose
            for (action in sequencer.onPose(pose, SystemClock.elapsedRealtime())) when (action) {
                GuideSequencer.Action.Arrived -> {
                    tones.stop()
                    tones.arrived(scope)
                    return LookAroundPhrases.arrived(target)
                }
                is GuideSequencer.Action.Overshot -> sayStep(LookAroundPhrases.overshot(action.toLeft))
                GuideSequencer.Action.TimedOut -> return LookAroundPhrases.gaveUp(target, pose.yawDeg)
            }
        }
        return LookAroundPhrases.GUIDE_STOPPED
    }

    /**
     * Says one step of the scan once VISOR has finished the last: VISOR's
     * voice speaks "over anything else", and a step cut off mid-word is worse
     * than one a moment late.
     */
    private suspend fun sayStep(text: String) {
        withTimeoutOrNull(STEP_WAIT_MS) { while (isSpeaking()) delay(SPEECH_POLL_MS) }
        say.step(text)
    }

    /** Attaches Motion to the session and waits for its first sample. Returns why it couldn't, or null. */
    private suspend fun startMotion(): String? {
        if (motion.status.value != HeadMotion.Status.RUNNING) {
            val attached = session.withSession { opened -> motion.attach(opened) }
            when (attached) {
                is HeldGlassesSession.Result.Done -> attached.value?.let { return it }
                is HeldGlassesSession.Result.Unavailable -> return LookAroundPhrases.LOST_HEAD_MOTION
            }
        }
        val first = withTimeoutOrNull(MOTION_START_TIMEOUT_MS) { motion.pose.first { it != null } }
        return if (first != null) null else motion.failure ?: NO_MOTION_DATA
    }

    /** The next head pose after [last], or null if none comes in [POSE_TIMEOUT_MS]: Motion has stopped. */
    private suspend fun nextPose(last: HeadingTracker.Pose?): HeadingTracker.Pose? =
        withTimeoutOrNull(POSE_TIMEOUT_MS) {
            motion.pose.first { it != null && it.atNanos != last?.atNanos && motion.status.value != HeadMotion.Status.FAILED }
        }

    /** One picture from the glasses camera, or null. */
    private suspend fun takePhoto(): Bitmap? = suspendCancellableCoroutine { continuation ->
        camera.capturePhoto(
            onPhoto = { bitmap -> if (continuation.isActive) continuation.resume(bitmap) },
            onError = { message ->
                Log.w(TAG, "Picture failed: $message")
                if (continuation.isActive) continuation.resume(null)
            },
        )
    }

    /** Keeps Motion and the session for [GUIDE_WINDOW_MS] more, then lets both go. */
    private fun openGuideWindow() {
        windowJob?.cancel()
        windowJob = scope.launch {
            delay(GUIDE_WINDOW_MS)
            // Not in the middle of guiding: wait for it to finish.
            job?.join()
            _found.value = null
            release()
        }
    }

    private fun release() {
        motion.detach()
        session.unpin(PIN_OWNER)
    }

    companion object {
        private const val TAG = "VisorLookAround"
        const val PIN_OWNER = "look_around"

        /** How long after a scan "guide me to" still works without looking around again. */
        const val GUIDE_WINDOW_MS = 2 * 60_000L

        private const val MOTION_START_TIMEOUT_MS = 6_000L
        private const val POSE_TIMEOUT_MS = 2_500L
        private const val DETECT_TIMEOUT_MS = 30_000L
        private const val STEP_WAIT_MS = 3_000L
        private const val SPEECH_POLL_MS = 50L

        const val NO_MOTION_DATA =
            "No head movement arrived from your glasses. " +
                "Check that Motion is enabled for VISOR in the Wearables Developer Center."
    }
}
