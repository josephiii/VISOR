package ucf.visor.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ucf.visor.data.VoiceCategory
import ucf.visor.data.Volumes
import java.util.Locale

/**
 * Says everything VISOR says out loud.
 *
 * A long answer (see [Readings]) is a reading: it can be paused, resumed and
 * stopped ([ReadingControls]), and [reading] says where it is. A reply or a
 * status update said over a reading pauses it rather than throwing it away,
 * so "VISOR GO" ("Listening.") never loses the user's place. A new answer,
 * long or short, replaces it: Resume would otherwise bring back an answer
 * the user has moved on from.
 *
 * @param loudness how loud each kind of speech plays, as a fraction of the
 *   media volume (0 is silent). Read each time something is spoken, so a
 *   change is heard on the very next word.
 * @param leadInMs how long until a chime that is still playing ends: speech
 *   waits for it rather than talking over it (see [Chimes]).
 *
 * It speaks in VISOR's default voice until told another ([useVoice]); which
 * one, given the user's choice and the connection, is [VoiceCatalog]'s call.
 */
class Speaker(
    context: Context,
    private val loudness: (VoiceCategory) -> Float,
    private val leadInMs: () -> Long = { 0L },
) : ReadingControls, VoiceChooser {

    /** Something VISOR said, and which kind of speech it was. */
    data class Utterance(val text: String, val category: VoiceCategory)

    private var tts: TextToSpeech? = null
    private var isReady = false

    /** What was asked to be said before the engine was ready, in order. */
    private val pending = mutableListOf<() -> Unit>()

    /** The engine reports progress on its own thread; the reading is kept on this one. */
    private val main = Handler(Looper.getMainLooper())

    /**
     * What voice navigation's "repeat that" says again: the last answer or
     * session update VISOR said out loud. Not its replies to voice commands,
     * or "repeat that" would repeat the "Listening." it had just said.
     */
    var lastUtterance: Utterance? = null
        private set

    private var speechRate = 1.0f

    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    override val voices: StateFlow<List<VoiceInfo>> = _voices.asStateFlow()

    /** The voice being spoken in, by name; null for VISOR's default. */
    private var voiceInUse: String? = null

    private val _reading = MutableStateFlow(ReadingState.NONE)
    override val reading: StateFlow<ReadingState> = _reading.asStateFlow()

    private val _readingText = MutableStateFlow<String?>(null)
    override val readingText: StateFlow<String?> = _readingText.asStateFlow()

    // The reading being read or paused: its pieces, the one being said (where
    // resume starts), and its volume, read again as it resumes.
    private var pieces: List<String> = emptyList()
    private var current = 0
    private var readingVolume: () -> Float = { 0f }

    /**
     * Goes up each time the reading is queued, paused or ended. Each piece's
     * utterance id carries the number it was queued under, so the engine's
     * reports about pieces that have since been flushed are ignored.
     */
    private var generation = 0

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(speechRate)
                tts?.setOnUtteranceProgressListener(progress)
                _voices.value = installedVoices()
                isReady = true
                pending.forEach { it() }
                pending.clear()
            } else {
                Log.e("VISOR TTS", "TTS Failed: $status")
            }

        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    override fun refreshVoices() {
        whenReady { _voices.value = installedVoices() }
    }

    /**
     * Speaks in [name] from the next word on, or in VISOR's default voice for
     * null, or for a voice the engine no longer has. A reading being read
     * carries on in the new voice from the start of its sentence: what was
     * already queued would otherwise finish in the old one, and if the old
     * one needs the internet that has just gone, it would stall.
     */
    override fun useVoice(name: String?) {
        whenReady {
            if (name == voiceInUse) return@whenReady
            val engine = tts ?: return@whenReady
            val voice = name?.let { n -> runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == n } }
            if (voice == null || engine.setVoice(voice) != TextToSpeech.SUCCESS) {
                // What VISOR has always done: the engine's default voice for US English.
                engine.language = Locale.US
                voiceInUse = null
            } else {
                voiceInUse = voice.name
            }
            // Some engines go back to their default rate with a new voice.
            engine.setSpeechRate(speechRate)
            if (_reading.value == ReadingState.READING) queueReading()
        }
    }

    /** The engine's voices; none when it can't say (some engines throw). */
    private fun installedVoices(): List<VoiceInfo> =
        runCatching { tts?.voices }.getOrNull().orEmpty().map { it.toInfo() }

    private fun Voice.toInfo() = VoiceInfo(
        name = name,
        locale = locale,
        needsInternet = isNetworkConnectionRequired,
        installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty(),
    )

    /**
     * Says [text] at its [category]'s volume. When that category is silenced
     * it says nothing, but still stops whatever VISOR was saying, as any new
     * words would have.
     *
     * A long answer is a reading, which replaces any reading before it. A
     * short answer ends any reading, too, even a paused one.
     */
    fun speak(text: String, category: VoiceCategory) {
        if (text.isBlank()) return
        if (category != VoiceCategory.VOICE_COMMANDS && !Volumes.isOff(loudness(category))) {
            lastUtterance = Utterance(text, category)
        }
        val volume = { loudness(category) }
        when {
            category != VoiceCategory.ANSWERS -> whenReady { say(text, volume()) }
            Readings.isLong(text) -> whenReady { read(text, volume) }
            else -> whenReady {
                endReading()
                say(text, volume())
            }
        }
    }

    /**
     * Says [text] at [volume], whatever the sliders are set to: for Settings
     * saying what a slider was just set to, and the head-motion lab's cues.
     * Never what "repeat that" repeats.
     */
    fun speakAt(text: String, volume: Float) {
        if (text.isBlank()) return
        whenReady { say(text, volume) }
    }

    private fun whenReady(action: () -> Unit) {
        if (isReady) action() else pending.add(action)
    }

    /** Something short: said at once, over anything else, and pausing a reading. */
    private fun say(text: String, volume: Float) {
        val engine = tts ?: return
        interruptReading()
        if (Volumes.isOff(volume)) {
            engine.stop()
            return
        }
        val params = params(volume)
        val mode = leadIn(engine)
        engine.speak(text, mode, params, UTTERANCE_ID)
    }

    /** Starts reading [text] from its first piece, in place of any other reading. */
    private fun read(text: String, volume: () -> Float) {
        pieces = Readings.pieces(text)
        current = 0
        readingVolume = volume
        // Before queueReading() says READING: see ReadingControls.readingText.
        _readingText.value = text
        queueReading()
    }

    /** Queues the reading from [current] to its end, flushing anything else being said. */
    private fun queueReading() {
        val engine = tts ?: return
        val volume = readingVolume()
        if (pieces.isEmpty() || Volumes.isOff(volume)) {
            engine.stop()
            endReading()
            return
        }
        generation++
        val params = params(volume)
        var mode = leadIn(engine)
        for (index in current until pieces.size) {
            engine.speak(pieces[index], mode, params, pieceId(generation, index))
            mode = TextToSpeech.QUEUE_ADD
        }
        _reading.value = ReadingState.READING
    }

    /**
     * How each utterance is said: at [volume]; and with a voice that needs
     * the internet, how long the engine may wait on its server before it
     * gives up. VISOR switches such a voice for an on-phone one as soon as
     * the phone is offline (see VoiceCatalog.voiceToUse), but a connection
     * can be too weak to use some time before it is counted as gone.
     */
    private fun params(volume: Float) = Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, NETWORK_TIMEOUT_MS)
    }

    /**
     * Waits out a chime that is still playing, in the speech queue itself, so
     * isSpeaking() covers it and the next thing said still cuts off both.
     * Returns how the words after it should be queued.
     */
    private fun leadIn(engine: TextToSpeech): Int {
        val wait = leadInMs()
        if (wait <= 0) return TextToSpeech.QUEUE_FLUSH
        engine.playSilentUtterance(wait, TextToSpeech.QUEUE_FLUSH, UTTERANCE_ID)
        return TextToSpeech.QUEUE_ADD
    }

    /** A reading being said is about to be talked over: it pauses where it is. */
    private fun interruptReading() {
        if (_reading.value != ReadingState.READING) return
        generation++
        _reading.value = ReadingState.PAUSED
    }

    private fun endReading() {
        generation++
        pieces = emptyList()
        current = 0
        _reading.value = ReadingState.NONE
        _readingText.value = null
    }

    override fun pause() {
        if (_reading.value != ReadingState.READING) return
        interruptReading()
        tts?.stop()
    }

    override fun resume() {
        if (_reading.value != ReadingState.PAUSED) return
        queueReading()
    }

    // interrupts speech w out killing engine, like a cancel btn mid-sentence
    override fun stop() {
        tts?.stop()
        endReading()
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    // kills whole engine, should b used in OnDestroy in MainActivity
    fun shutdown() {
        main.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()

    }

    /** Which piece of the reading is being said, and when the last one is done. */
    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            main.post { onPieceStarted(utteranceId) }
        }

        override fun onDone(utteranceId: String) {
            main.post { onPieceEnded(utteranceId) }
        }

        // A piece the engine couldn't say is passed over, as the engine does
        // itself, so a failed last piece can't leave the controls up forever.
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            main.post { onPieceEnded(utteranceId) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            main.post { onPieceEnded(utteranceId) }
        }
    }

    private fun onPieceStarted(utteranceId: String) {
        val index = pieceIndex(utteranceId) ?: return
        current = index
    }

    private fun onPieceEnded(utteranceId: String) {
        val index = pieceIndex(utteranceId) ?: return
        if (index == pieces.lastIndex) endReading()
    }

    /** The piece [utteranceId] names, if it is one of the reading as it is queued now. */
    private fun pieceIndex(utteranceId: String): Int? {
        val parts = utteranceId.split(':')
        if (parts.size != 3 || parts[0] != READING_ID) return null
        if (parts[1].toIntOrNull() != generation) return null
        return parts[2].toIntOrNull()
    }

    private fun pieceId(generation: Int, index: Int) = "$READING_ID:$generation:$index"

    private companion object {
        const val UTTERANCE_ID = "VISOR_SPEAKING"
        const val READING_ID = "VISOR_READING"

        /** See [params]: a sentence starting this late is better said by an on-phone voice. */
        const val NETWORK_TIMEOUT_MS = "2000"
    }
}
