package ucf.visor.motion

/**
 * Says when the head has held still long enough for a sharp photo: its
 * angular speed under [thresholdDegPerSec] for [holdMs] without a break,
 * timed by the glasses' own sample clock (Bluetooth delivers samples in
 * bursts; their timestamps don't).
 *
 * The default threshold is the lab's measured one: on Ray-Ban Display glasses,
 * natively, a wearer asked to hold still stayed under 6.4 °/s 95% of the time
 * (C1 stillness hold, documentation/testing/imu-characterization-report.md,
 * "Recommended capture-gate threshold").
 */
class SteadyGate(
    var thresholdDegPerSec: Double = STILL_DEG_PER_SEC,
    private val holdMs: Long = HOLD_MS,
) {
    private var stillSinceNanos: Long? = null

    /** Adds one reading; returns whether the head is steady now. */
    fun update(atNanos: Long, speedDegPerSec: Double): Boolean {
        if (speedDegPerSec >= thresholdDegPerSec) {
            stillSinceNanos = null
            return false
        }
        val since = stillSinceNanos ?: atNanos.also { stillSinceNanos = it }
        // A clock that went back starts the hold again.
        if (atNanos < since) {
            stillSinceNanos = atNanos
            return false
        }
        return (atNanos - since) / 1_000_000 >= holdMs
    }

    fun reset() {
        stillSinceNanos = null
    }

    companion object {
        /** The lab's capture-gate threshold: p95 head speed during a deliberate hold, native, Ray-Ban Display. */
        const val STILL_DEG_PER_SEC = 6.4

        /**
         * The looser threshold used once a wearer has tried to hold still for
         * a while: a tremor, or a head that can't settle, should still get its
         * photo, at some cost in sharpness, rather than none at all.
         */
        const val RELAXED_DEG_PER_SEC = 15.0

        /** How long the head must stay under the threshold. */
        const val HOLD_MS = 300L
    }
}
