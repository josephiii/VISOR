package ucf.visor.tts

/**
 * Whether VISOR is still saying something, from the moment it asks the speech
 * engine to say it until the engine reports it said.
 *
 * The engine's own TextToSpeech.isSpeaking() is false until the sound starts,
 * which can be a good part of a second after VISOR asks (longer for a voice
 * that needs the internet). Anything that waits for VISOR to finish speaking
 * before opening the microphone, such as each setup question, found it
 * "finished" before it had begun: the microphone opened, the phone buzzed and
 * the edge glow breathed over the question, and the recognizer heard VISOR's
 * own words, so the answer was cut short. Speech rate doesn't matter here:
 * this follows the engine's reports, not a guess at how long the words take.
 *
 * [Speaker] calls [queued] with the id of the last thing it queues and
 * [ended] as the engine reports each utterance done, stopped or failed.
 *
 * @param now the time in milliseconds (SystemClock.elapsedRealtime on a phone).
 */
internal class QueuedSpeech(private val now: () -> Long) {

    @Volatile
    private var lastId: String? = null

    @Volatile
    private var queuedAt = 0L

    /** [id] is the last utterance queued: VISOR is speaking until it has [ended]. */
    fun queued(id: String) {
        queuedAt = now()
        lastId = id
    }

    /** The engine reports [id] done, stopped or failed. Reports about anything queued before the last are ignored. */
    fun ended(id: String) {
        if (id == lastId) lastId = null
    }

    /** Everything queued was stopped or flushed with nothing after it. */
    fun cleared() {
        lastId = null
    }

    /**
     * Speaking while the engine says so, and, before it does, while the last
     * utterance queued hasn't ended, for up to [StartGraceMs] after it was
     * queued: an engine that never reports can't keep VISOR "speaking" forever.
     */
    fun isSpeaking(engineSpeaking: Boolean): Boolean {
        if (engineSpeaking) return true
        if (lastId == null) return false
        return now() - queuedAt < StartGraceMs
    }

    companion object {
        /**
         * Longest VISOR counts as speaking before the engine starts the sound:
         * above the 2 s an internet voice may wait on its server
         * (Speaker.NETWORK_TIMEOUT_MS) and a chime it waits out.
         */
        const val StartGraceMs = 5_000L
    }
}
