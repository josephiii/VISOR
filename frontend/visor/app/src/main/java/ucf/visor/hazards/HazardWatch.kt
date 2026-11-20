package ucf.visor.hazards

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ucf.visor.capture.Availability
import ucf.visor.capture.availability
import ucf.visor.data.VisionPlan
import ucf.visor.network.Hazard
import ucf.visor.stt.CameraCommand
import ucf.visor.tts.SpokenPhrases

/** How often hazard watch looks, in milliseconds. A guess, to be tuned on a device. */
const val HAZARD_WATCH_INTERVAL_MS = 3_000L

/** After this many failed looks in a row, hazard watch says it can't see (once). */
const val HAZARD_WATCH_FAILURES_BEFORE_SAYING = 3

/**
 * How long hazard watch waits between looks once it has said it can't see:
 * glasses that are refusing sessions are not asked every few seconds.
 */
const val HAZARD_WATCH_RETRY_INTERVAL_MS = 15_000L

/**
 * Below this the detector is guessing, and a watch that speaks by itself
 * should not guess. A starting point, to be tuned on real photos.
 */
const val HAZARD_WATCH_MIN_CONFIDENCE = 0.35f

/** How long the server may take over one of the watch's pictures before the look counts as failed. */
const val HAZARD_WATCH_DETECT_TIMEOUT_MS = 10_000L

/** The width, in pixels, a picture is scaled down to before hazard watch sends it. */
const val HAZARD_WATCH_FRAME_WIDTH = 640

/** The JPEG quality hazard watch sends at. */
const val HAZARD_WATCH_JPEG_QUALITY = 70

/** What hazard watch says aloud, and whether VISOR is already saying something. */
interface HazardWatchSpeaker {
    fun say(text: String)
    fun isSpeaking(): Boolean
}

/**
 * Hazard watch: looks for hazards every [intervalMs] and says only what is new
 * ([HazardAnnouncer]). Run [run] in a coroutine and cancel it to stop.
 *
 * One look at a time: the next waits for the last to finish. A look is
 * skipped while VISOR is speaking, while another camera feature is working
 * or VISOR is listening for a command ([busy]), and while offline. [P] is the picture ([capture] returns null
 * when it could not take one); [widthOf] is its width in pixels.
 */
class HazardWatch<P>(
    private val speaker: HazardWatchSpeaker,
    private val announcer: HazardAnnouncer,
    private val capture: suspend () -> P?,
    private val detect: suspend (P) -> List<Hazard>?,
    private val widthOf: (P) -> Int,
    private val isOnline: () -> Boolean,
    private val busy: () -> Boolean,
    private val clockMs: () -> Long,
    private val limit: () -> Int = { Int.MAX_VALUE },
    private val focus: () -> VisionPlan.HazardFocus = { VisionPlan.HazardFocus.NEAREST },
    private val minConfidence: Float = HAZARD_WATCH_MIN_CONFIDENCE,
    private val intervalMs: Long = HAZARD_WATCH_INTERVAL_MS,
    private val retryIntervalMs: Long = HAZARD_WATCH_RETRY_INTERVAL_MS,
) {
    private var failures = 0
    private var pausedOffline = false

    suspend fun run() {
        failures = 0
        pausedOffline = false
        while (currentCoroutineContext().isActive) {
            checkOnce()
            delay(if (failures >= HAZARD_WATCH_FAILURES_BEFORE_SAYING) retryIntervalMs else intervalMs)
        }
    }

    private suspend fun checkOnce() {
        if (CameraCommand.CHECK_HAZARDS.availability(isOnline()) == Availability.LOCKED) {
            if (!pausedOffline) {
                pausedOffline = true
                speaker.say(SpokenPhrases.HAZARD_WATCH_PAUSED_OFFLINE)
            }
            return
        }
        if (pausedOffline) {
            pausedOffline = false
            speaker.say(SpokenPhrases.HAZARD_WATCH_BACK)
        }
        if (speaker.isSpeaking() || busy()) return

        val picture = try {
            capture()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val hazards = picture?.let {
            try {
                detect(it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        if (picture == null || hazards == null) {
            failures++
            if (failures == HAZARD_WATCH_FAILURES_BEFORE_SAYING) speaker.say(SpokenPhrases.HAZARD_WATCH_CANT_SEE)
            return
        }
        failures = 0

        // The look took a while: VISOR may have started speaking, or another
        // feature started, since it began. Its answer comes first; this look
        // is dropped, unremembered, and the next one says what is still there.
        if (speaker.isSpeaking() || busy()) return

        // Only what the detector is fairly sure of, at a distance that can be said.
        val sure = hazards.filter { it.conf >= minConfidence && it.depth.isFinite() && it.depth >= 0f }
        val width = widthOf(picture)
        val count = limit()
        val news = announcer.next(sure, width, clockMs(), count)
        if (news.isNotEmpty()) speaker.say(SpokenPhrases.hazardsAhead(news, width, count, focus()))
    }
}
