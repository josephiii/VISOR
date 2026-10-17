package ucf.visor.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ucf.visor.data.SoundCategory
import ucf.visor.data.Volumes
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * VISOR's chimes: a session starting or ending, confirmed the moment it
 * happens, before the words for it begin, and without words at all for a user
 * who has turned session updates off.
 *
 * Synthesized on the phone the way the head-motion lab's cues are
 * (motionlab/CuePlayer), and played as media, like VISOR's voice, so they come
 * from the glasses when the glasses are the phone's audio device. The start
 * rises and the end falls: a difference of pitch contour, not just of
 * loudness, so it holds up for a listener who has lost some of their hearing
 * in one frequency band. Both stay between 500 and 1,000 Hz, below the high
 * frequencies age-related hearing loss takes first.
 *
 * @param loudness each sound category's volume, as a fraction of the media
 *   volume (0 is off). Read whenever a chime plays, so Settings changes apply at once.
 */
class Chimes(private val loudness: (SoundCategory) -> Float) {

    /** One sine note: [frequency] Hz, starting [offsetSec] in, lasting [durationSec]. */
    data class Note(val frequency: Double, val offsetSec: Double, val durationSec: Double)

    enum class Chime(val category: SoundCategory, val notes: List<Note>) {
        /** Three notes up: a session has started. */
        SESSION_START(
            SoundCategory.SESSION_CHIMES,
            listOf(Note(C5, 0.0, 0.1), Note(E5, 0.12, 0.1), Note(G5, 0.24, 0.18)),
        ),

        /** The same three notes down: the session has ended. */
        SESSION_END(
            SoundCategory.SESSION_CHIMES,
            listOf(Note(G5, 0.0, 0.1), Note(E5, 0.12, 0.1), Note(C5, 0.24, 0.18)),
        );

        /** How long it plays. Speech waits this long before starting ([Speaker]). */
        val durationMs: Long = (notes.maxOf { it.offsetSec + it.durationSec } * 1000).roundToLong()
    }

    private val tracks = mutableMapOf<Chime, AudioTrack>()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var endsAt = 0L

    init {
        for (chime in Chime.entries) {
            runCatching { tracks[chime] = buildTrack(render(chime.notes)) }
                .onFailure { Log.w(TAG, "Could not prepare chime $chime", it) }
        }
    }

    /** Plays [chime] at its category's volume, or not at all when that is off. */
    fun play(chime: Chime) {
        val volume = loudness(chime.category)
        if (Volumes.isOff(volume)) return
        val track = tracks[chime] ?: return
        runCatching {
            handler.removeCallbacksAndMessages(chime)
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.setVolume(volume)
            track.play()
            endsAt = SystemClock.elapsedRealtime() + chime.durationMs
            // A static track reports itself playing until it is stopped, long
            // after its last sample: without this, Android would go on listing
            // VISOR as playing audio after every chime.
            handler.postDelayed(
                { runCatching { track.stop() } },
                chime,
                chime.durationMs + STOP_AFTER_END_MS,
            )
        }.onFailure { Log.w(TAG, "Could not play $chime", it) }
    }

    /** How long until the chime playing now ends, in milliseconds: 0 when none is. */
    fun remainingMs(): Long = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun release() {
        handler.removeCallbacksAndMessages(null)
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }

    private fun render(notes: List<Note>): ShortArray {
        val end = notes.maxOf { it.offsetSec + it.durationSec } + TAIL_SEC
        val pcm = DoubleArray((end * SAMPLE_RATE).toInt())
        for (note in notes) {
            val start = (note.offsetSec * SAMPLE_RATE).toInt()
            val length = (note.durationSec * SAMPLE_RATE).toInt()
            val attack = (ATTACK_SEC * SAMPLE_RATE).toInt().coerceAtLeast(1)
            val release = min(length / 4, (RELEASE_SEC * SAMPLE_RATE).toInt()).coerceAtLeast(1)
            for (i in 0 until length) {
                // A quick rise and a bell-like decay make a chime rather than a
                // beep, and ramps instead of hard edges, which click on small speakers.
                val strike = if (i < attack) i.toDouble() / attack else exp(-DECAY * (i - attack) / length)
                val fadeOut = min(1.0, (length - i).toDouble() / release)
                val index = start + i
                if (index < pcm.size) {
                    pcm[index] += NOTE_GAIN * strike * fadeOut * sin(2 * PI * note.frequency * i / SAMPLE_RATE)
                }
            }
        }
        return ShortArray(pcm.size) {
            (pcm[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    private companion object {
        const val TAG = "VisorChimes"
        const val SAMPLE_RATE = 44_100

        // Notes, in Hz.
        const val C5 = 523.25
        const val E5 = 659.25
        const val G5 = 783.99

        const val NOTE_GAIN = 0.5
        const val ATTACK_SEC = 0.005
        const val RELEASE_SEC = 0.01
        const val DECAY = 3.0
        const val TAIL_SEC = 0.02

        /** Margin past a chime's last note, and its silent tail, before its track is stopped. */
        const val STOP_AFTER_END_MS = 100L
    }
}
