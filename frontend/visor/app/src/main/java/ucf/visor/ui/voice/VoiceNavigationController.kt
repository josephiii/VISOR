package ucf.visor.ui.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ucf.visor.stt.CommandRecognizer

/**
 * What voice navigation is doing right now: shown by the screen's edge glow
 * (VoiceListeningGlow) and felt as a buzz (VisorLayout).
 */
enum class VoiceNavState {
    IDLE,

    /**
     * VISOR is asking a setup question, and will open the microphone once it
     * has finished. Neither shown nor felt: the glow and the buzz mean "speak
     * now", and came on over the question when they began here. The
     * microphone is spoken for all the same, so not [IDLE].
     */
    ASKING,

    /**
     * VISOR is saying "Listening." after "VISOR GO", and the microphone is
     * about to open. Not yet the time to speak; the steady glow says the wake
     * phrase was heard.
     */
    PROMPTING,

    /** The microphone is open: say the command now. */
    LISTENING,

    /** The microphone has closed, and VISOR is working out what was said. */
    PROCESSING,
}

/**
 * Owns the "VISOR GO" -> free-speech -> [VoiceCommand] pipeline.
 * Does not itself run the KWS wake-word engine (VisorEngine keeps a
 * single [ucf.visor.stt.Listener] for both the OCR wake phrases and "VISOR GO",
 * so mic ownership has one clear home) — call [activate] when "VISOR GO" fires.
 *
 * [pauseWakeListening] / [resumeWakeListening] hand exclusive mic access to
 * [CommandRecognizer] for the duration of one utterance: sherpa's KWS engine and
 * Android's SpeechRecognizer can't both hold the microphone at once.
 *
 * Emits parsed commands on [commands] rather than storing "the current command"
 * in a StateFlow, so a screen transition or recomposition can never re-deliver
 * (or drop) an already-handled command.
 */
class VoiceNavigationController(
    context: Context,
    private val speak: (String) -> Unit,
    private val pauseWakeListening: () -> Unit,
    private val resumeWakeListening: () -> Unit,
    private val isSpeaking: () -> Boolean = { false },
) {
    private val commandRecognizer = CommandRecognizer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var captureJob: Job? = null

    private val _state = MutableStateFlow(VoiceNavState.IDLE)
    val state: StateFlow<VoiceNavState> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 1)
    val commands: SharedFlow<VoiceCommand> = _commands.asSharedFlow()

    @Volatile
    private var enabled = true

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        // Turning voice nav off mid-capture must still hand the mic back to
        // the KWS wake-word engine — that engine also spots the OCR reading
        // phrases, which stay on regardless of this setting.
        if (!isEnabled && _state.value != VoiceNavState.IDLE) {
            captureJob?.cancel()
            captureJob = null
            commandRecognizer.cancel()
            resumeWakeListening()
            _state.value = VoiceNavState.IDLE
        }
    }

    /** Call when the "VISOR GO" wake phrase is heard. No-ops if voice nav is off. */
    fun activate() {
        if (!enabled) return
        speak("Listening.")
        captureUtterance(whileSpeaking = VoiceNavState.PROMPTING) { text ->
            val command = text?.let(VoiceCommandParser::parse) ?: VoiceCommand.Unrecognized
            if (command == VoiceCommand.Unrecognized) {
                speak("Sorry, I didn't catch a command.")
            }
            _commands.tryEmit(command)
        }
    }

    /**
     * Captures one free-form utterance and hands the raw recognized text to
     * [onResult] (null if nothing usable was heard) — used directly by
     * ProfileCreationScreen's voice-first onboarding questions, which need the
     * raw words spoken (a name, a description), not a [VoiceCommand].
     *
     * Deliberately speaks nothing itself: [Speaker.speak] uses QUEUE_FLUSH, so
     * an announcement here would cut off whatever the caller just said a
     * moment earlier (e.g. the onboarding wizard's spoken question) before the
     * user ever heard it. Callers with nothing already spoken (see [activate])
     * should speak their own cue first.
     *
     * The microphone opens only once VISOR has finished speaking (Speaker's
     * isSpeaking() counts from the moment something is queued, see
     * tts.QueuedSpeech), at whatever speech rate, and a moment after that, so
     * the recognizer hears the answer rather than the end of the question.
     *
     * @param whileSpeaking what [state] is until then: [VoiceNavState.ASKING]
     *   (nothing shown) for a setup question, [VoiceNavState.PROMPTING] (a
     *   steady glow) after "VISOR GO".
     */
    fun captureUtterance(
        whileSpeaking: VoiceNavState = VoiceNavState.ASKING,
        onResult: (String?) -> Unit,
    ) {
        if (!enabled) {
            onResult(null)
            return
        }
        _state.value = whileSpeaking
        pauseWakeListening()
        captureJob = scope.launch {
            // Capped, so a speech engine stuck "speaking" can never keep the
            // microphone closed for good. Long enough for the longest setup
            // question at the slowest speech rate.
            var waited = 0L
            while (isSpeaking() && waited < MAX_SPEECH_WAIT_MS) {
                delay(SPEECH_POLL_MS)
                waited += SPEECH_POLL_MS
            }
            // isSpeaking() can turn false a beat before the last of the sound
            // has played out of the speaker and died away.
            delay(POST_SPEECH_GUARD_MS)
            commandRecognizer.listen(
                // Listening means the microphone is open, not that VISOR has
                // asked: the glow breathes, and the phone buzzes, only now.
                onReady = { _state.value = VoiceNavState.LISTENING },
                onSpeechEnded = { _state.value = VoiceNavState.PROCESSING },
            ) { text ->
                resumeWakeListening()
                _state.value = VoiceNavState.IDLE
                onResult(text)
            }
        }
    }

    /**
     * Speaks [text] with the KWS wake-word listener paused for its duration
     * (plus a short trailing buffer) — for any text that might literally say
     * the wake phrase itself out loud, like the voice-nav tip explaining
     * "VISOR GO". Without this, the always-on KWS listener can hear the
     * device's own TTS say "VISOR GO" and fire a false [activate] in the
     * middle of the sentence — turning voice nav on would immediately,
     * audibly interrupt its own explanation of how to use it.
     *
     * Unlike [captureUtterance], this never starts [CommandRecognizer]
     * afterward — it only pauses/resumes the wake listener around the speech,
     * same idea as the user's own suggestion to delay wake-word listening
     * right after voice nav is first turned on.
     */
    fun speakGuarded(text: String) {
        pauseWakeListening()
        speak(text)
        scope.launch {
            var waited = 0L
            while (isSpeaking() && waited < MAX_SPEECH_WAIT_MS) {
                delay(SPEECH_POLL_MS)
                waited += SPEECH_POLL_MS
            }
            // isSpeaking() can flip false a beat before the last of the audio
            // actually finishes playing/decaying out of the mic's earshot.
            delay(POST_SPEECH_GUARD_MS)
            resumeWakeListening()
        }
    }

    /**
     * Cancels an in-flight [captureUtterance] without delivering its result, and
     * resumes wake listening immediately. SpeechRecognizer.cancel() doesn't
     * reliably invoke the pending RecognitionListener callback, so without this
     * a fast touch interaction racing an active voice capture (e.g. tapping
     * "Skip" in onboarding the instant a new question starts listening) could
     * leave the KWS wake-word engine paused indefinitely. Call this whenever a
     * touch action makes an in-flight capture moot.
     */
    fun cancelCapture() {
        if (_state.value == VoiceNavState.IDLE) return
        // Also cancels the speech-completion wait in captureUtterance, if it
        // hasn't started the recognizer yet — otherwise that coroutine would
        // still call commandRecognizer.listen() after resumeWakeListening()
        // below runs, handing the mic to two engines at once.
        captureJob?.cancel()
        captureJob = null
        commandRecognizer.cancel()
        resumeWakeListening()
        _state.value = VoiceNavState.IDLE
    }

    fun shutdown() {
        scope.cancel()
        commandRecognizer.shutdown()
    }

    private companion object {
        const val SPEECH_POLL_MS = 100L
        // Was 6 s, which a setup question can outlast: the microphone then
        // opened while VISOR was still asking it.
        const val MAX_SPEECH_WAIT_MS = 45_000L
        const val POST_SPEECH_GUARD_MS = 400L
    }
}
