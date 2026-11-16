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
 * ([busy]), and while offline. [P] is the picture ([capture] returns null
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
    private val intervalMs: Long = HAZARD_WATCH_INTERVAL_MS,
) {
    private var failures = 0
    private var pausedOffline = false

    suspend fun run() {
        failures = 0
        pausedOffline = false
        while (currentCoroutineContext().isActive) {
            checkOnce()
            delay(intervalMs)
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

        val width = widthOf(picture)
        val news = announcer.next(hazards, width, clockMs())
        if (news.isNotEmpty()) speaker.say(SpokenPhrases.hazardsAhead(news, width, limit(), focus()))
    }
}
