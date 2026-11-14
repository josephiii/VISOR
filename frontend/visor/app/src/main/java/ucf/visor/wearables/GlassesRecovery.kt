package ucf.visor.wearables

/**
 * What VISOR does, step by step, when a glasses picture fails in a way a
 * recovery may fix. Pure, for unit tests (GlassesRecoveryTest).
 *
 * Built from three field logs (Ray-Ban Meta, Galaxy A52, 2026-11-13) and
 * Meta's own guidance in meta-wearables-dat-android issues #138 and #192:
 * - Glasses that are off or out of range are not a link problem: say so.
 * - The glasses' microphone (a Bluetooth call link) open while a session
 *   starts can end it on some firmware. The camera wins: VISOR moves to the
 *   phone's microphone for a while.
 * - If the SDK still sees the glasses, a fresh session is all it takes.
 * - If it has lost them, it will not find them again by itself in this
 *   process: it retries only when Android reports a Bluetooth change (#208),
 *   and the glasses never left the phone. `Wearables.reset()` +
 *   `initialize()` does not help on MWDAT 1.0.0: `reset()` only drops the SDK's
 *   reference to its instance without shutting it down, and the new one made
 *   no connection attempt in two field logs. Restarting VISOR is what always
 *   worked. With no screen showing, VISOR can't restart itself (Android would
 *   not let it have the microphone back), so it asks the wearer to open it,
 *   and restarts then.
 */
object GlassesRecovery {

    enum class Step {
        /** The glasses are not connected to the phone at all: nothing to recover. */
        GIVE_UP,

        /** Let go of the glasses' microphone, then try again. */
        YIELD_MIC,

        /** The SDK still sees the glasses: drop the session and try again in a fresh one. */
        RETRY,

        /** Restart VISOR, which is on screen. */
        RESTART_VISOR,

        /** Ask the wearer to open VISOR; it restarts as it opens. */
        ASK_TO_OPEN_VISOR,
    }

    data class Situation(
        /** The glasses are connected to the phone for audio, whatever the SDK thinks. */
        val glassesOnPhone: Boolean,
        /** VISOR is hearing through the glasses' microphone right now. */
        val micOnGlasses: Boolean,
        /** The SDK reports the glasses as connected. */
        val sdkSeesGlasses: Boolean,
        /** This recovery has already retried in a fresh session. */
        val retried: Boolean,
        val screenShown: Boolean,
        /** How long since VISOR last restarted itself for this, or null if it never has. */
        val sinceLastRestartMs: Long?,
    )

    /** How a recovery ended, for the picture that asked for it. */
    sealed class Outcome {
        /** Try the picture once more. */
        object TryAgain : Outcome()

        /** Give up on this picture, saying [spoken] (or the picture's own problem when null). */
        data class Stop(val spoken: String? = null) : Outcome()
    }

    /**
     * VISOR restarts itself at most this often, so glasses that really are
     * broken can't make it restart in a loop. Short: a restart is the fix, and
     * each one follows a command the wearer gave.
     */
    const val MIN_RESTART_GAP_MS = 60_000L

    fun next(s: Situation): Step = when {
        !s.glassesOnPhone -> Step.GIVE_UP
        s.micOnGlasses -> Step.YIELD_MIC
        s.sdkSeesGlasses && !s.retried -> Step.RETRY
        s.sdkSeesGlasses -> Step.GIVE_UP
        s.sinceLastRestartMs != null && s.sinceLastRestartMs < MIN_RESTART_GAP_MS -> Step.GIVE_UP
        s.screenShown -> Step.RESTART_VISOR
        else -> Step.ASK_TO_OPEN_VISOR
    }
}
