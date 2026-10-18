package ucf.visor.ui.feedback

import android.animation.ValueAnimator

/**
 * Whether the system's animations are on. With them off (Accessibility >
 * Remove animations, or the animator duration scale at 0), VISOR's breathing
 * and sweeping rings hold still in their most visible state rather than
 * move, so every state still shows.
 *
 * VISOR's motion follows a few rules: nothing pulses faster than once a
 * second, nowhere near Section 508's three flashes a second; every motion has
 * a twin that doesn't move (text, an icon, or a vibration); and nothing moves
 * behind text being read.
 */
fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
