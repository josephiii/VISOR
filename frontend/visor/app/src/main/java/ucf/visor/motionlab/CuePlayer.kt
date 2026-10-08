package ucf.visor.motionlab

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The lab's sounds: the same tone palette as the web IMU Lab
 * (frontend/visor-webapp/protocol.js), synthesized on the phone.
 *
 * Sound is not decoration here. During a static trial the glasses are lying on
 * a table and during a VOR trial the wearer's eyes are on a fixed target, so
 * sound is the only channel that says a trial started, when to turn, and that
 * it ended. Each sound is distinct in pitch contour, not just loudness, so they
 * are distinguishable for a wearer with hearing loss in one frequency band.
 *
 * Routed as media, like VISOR's speech, so they play through the glasses'
 * speakers when the glasses are the phone's audio device.
 */
class CuePlayer {

    enum class Sound(val tones: List<Tone>) {
        /** Countdown blip over the final seconds of preparation. */
        TICK(listOf(Tone(880.0, 0.0, 0.08, 0.35))),

        /** Rising two-tone: recording has begun. */
        START(listOf(Tone(523.25, 0.0, 0.14, 0.5), Tone(783.99, 0.15, 0.22, 0.5))),

        /** Falling three-tone, deliberately unlike the start chime. */
        FINISH(listOf(Tone(783.99, 0.0, 0.14, 0.55), Tone(659.25, 0.16, 0.14, 0.55), Tone(523.25, 0.32, 0.30, 0.55))),

        /** Low double tone: the trial was stopped or failed. */
        ABORT(listOf(Tone(392.00, 0.0, 0.16, 0.5), Tone(311.13, 0.18, 0.28, 0.5))),

        /** Soft blip before a spoken cue word. */
        CUE(listOf(Tone(1046.5, 0.0, 0.06, 0.3))),

        /** Metronome: first step of a paced movement (left / up). */
        BEAT_HIGH(listOf(Tone(1046.5, 0.0, 0.07, 0.45))),

        /** Metronome: second step (right / down). */
        BEAT_LOW(listOf(Tone(587.33, 0.0, 0.07, 0.45))),
    }

    /** One sine burst: [frequency] Hz, starting [offsetSec] in, lasting [durationSec], at [gain] 0..1. */
    data class Tone(val frequency: Double, val offsetSec: Double, val durationSec: Double, val gain: Double)

    private val tracks = mutableMapOf<Sound, AudioTrack>()

    init {
        for (sound in Sound.entries) {
            runCatching { tracks[sound] = buildTrack(render(sound)) }
                .onFailure { Log.w(TAG, "Could not prepare sound $sound", it) }
        }
    }

    fun play(sound: Sound) {
        val track = tracks[sound] ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        }.onFailure { Log.w(TAG, "Could not play $sound", it) }
    }

    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }

    private fun render(sound: Sound): ShortArray {
        val end = sound.tones.maxOf { it.offsetSec + it.durationSec } + 0.02
        val pcm = DoubleArray((end * SAMPLE_RATE).toInt())
        for (tone in sound.tones) {
            val start = (tone.offsetSec * SAMPLE_RATE).toInt()
            val length = (tone.durationSec * SAMPLE_RATE).toInt()
            // Short linear ramps rather than hard edges: a square-edged
            // envelope clicks audibly on small speakers.
            val ramp = min(length / 4, (0.012 * SAMPLE_RATE).toInt()).coerceAtLeast(1)
            for (i in 0 until length) {
                val envelope = when {
                    i < ramp -> i.toDouble() / ramp
                    i > length - ramp -> (length - i).toDouble() / ramp
                    else -> 1.0
                }
                val index = start + i
                if (index < pcm.size) {
                    pcm[index] += tone.gain * envelope * sin(2 * PI * tone.frequency * i / SAMPLE_RATE)
                }
            }
        }
        return ShortArray(pcm.size) { (pcm[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.8).toInt().toShort() }
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
        const val TAG = "VisorCuePlayer"
        const val SAMPLE_RATE = 44_100
    }
}
