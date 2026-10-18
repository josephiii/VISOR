package ucf.visor.ui.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Everything VISOR says by vibration, one pattern per meaning, so each can be
 * told apart without looking or listening:
 *
 *  - [sessionStarted]: two short pulses. [sessionEnded]: one long pulse.
 *  - [listeningStarted]: one firm buzz as the microphone opens for a voice
 *    command. [listeningStopped]: a light tick as it closes.
 *  - [tick]: a light tap as a button or choice is pressed.
 *  - [rejected]: three short pulses, for a control that is locked.
 *
 * Every one of them stays quiet while [enabled] says the user has turned
 * vibration off in Settings. A tap's tick also follows the phone's own touch
 * feedback setting, as any app's would.
 */
class Haptics(context: Context, private val enabled: () -> Boolean) {

    private val vibrator: Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    fun tick() = touch(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

    fun rejected() = touch(waveform(Pulse, Gap, Pulse, Gap, Pulse))

    fun sessionStarted() = status(waveform(Pulse, Gap, Pulse))

    fun sessionEnded() = status(waveform(LongPulse))

    fun listeningStarted() = status(waveform(Buzz))

    fun listeningStopped() = status(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

    /** A response to the user's own touch. */
    private fun touch(effect: VibrationEffect) = vibrate(effect, VibrationAttributes.USAGE_TOUCH)

    /**
     * Something VISOR is telling the user about its state, marked as an
     * accessibility vibration, which is what it is: a way to know what VISOR
     * is doing without seeing the screen.
     */
    private fun status(effect: VibrationEffect) = vibrate(effect, VibrationAttributes.USAGE_ACCESSIBILITY)

    private fun vibrate(effect: VibrationEffect, usage: Int) {
        if (!enabled()) return
        val target = vibrator?.takeIf { it.hasVibrator() } ?: return
        // Saying what a vibration is for needs Android 13. Before that, it is
        // simply played.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.vibrate(effect, VibrationAttributes.createForUsage(usage))
        } else {
            target.vibrate(effect)
        }
    }

    /** Alternating on and off times in milliseconds, starting with on. */
    private fun waveform(vararg onOff: Long): VibrationEffect {
        val timings = longArrayOf(0L) + onOff
        val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 1) VibrationEffect.DEFAULT_AMPLITUDE else 0 }
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private companion object {
        const val Pulse = 60L
        const val Gap = 90L
        const val LongPulse = 450L
        const val Buzz = 120L
    }
}

/**
 * The [Haptics] for the screen being shown, provided by VisorLayout. Null in
 * previews and tests, where nothing vibrates.
 */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }
