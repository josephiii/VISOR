package ucf.visor.lookaround

import kotlin.math.atan
import kotlin.math.tan
import kotlin.math.PI

/**
 * Where Look around takes its pictures: head turns, in degrees from where the
 * wearer faced when they asked (left is positive), in the order they are
 * visited. Ahead first, while the wearer is still facing that way, then the
 * left, then the right.
 *
 * **Why these angles.** One glasses photo from the camera stream is only
 * [CameraOptics.HORIZONTAL_FOV_DEG] wide. Stops [STOP_SPACING_DEG] apart
 * overlap by a few degrees, so nothing falls between two pictures:
 *
 *  - [STANDARD]: ahead, 35° left, 35° right. About 113° in all, against 43°
 *    for one picture: a comfortable turn for anyone.
 *  - [WIDE]: adds 70° left and right, about 183° in all. For someone with
 *    trouble seeing the sides (VisionPlan), the part of the room they are most
 *    likely to miss is the part a single picture leaves out.
 */
data class ScanPlan(val stops: List<Double>) {

    init {
        require(stops.isNotEmpty() && stops.first() == 0.0) { "A scan starts ahead" }
    }

    /** How far the pictures reach either side of ahead, in degrees. */
    val reachDeg: Double
        get() = stops.maxOf { kotlin.math.abs(it) } + CameraOptics.HORIZONTAL_FOV_DEG / 2

    companion object {
        /** How far apart neighbouring stops are. */
        const val STOP_SPACING_DEG = 35.0

        val STANDARD = ScanPlan(listOf(0.0, STOP_SPACING_DEG, -STOP_SPACING_DEG))

        val WIDE = ScanPlan(
            listOf(0.0, STOP_SPACING_DEG, 2 * STOP_SPACING_DEG, -STOP_SPACING_DEG, -2 * STOP_SPACING_DEG),
        )
    }
}

/**
 * The glasses camera's geometry, for turning a place in a photo into a
 * direction.
 *
 * Measured, not assumed: the head-motion lab's slow-pan calibration on Ray-Ban
 * Display glasses (V4, documentation/testing/imu-characterization-report.md)
 * found the camera stream's image moves 8.02 pixels per degree of head turn
 * at its 360-pixel width, R² 0.97. That is a focal length of
 * 8.02 × 180 / π = 459.5 px, so the stream's width spans
 * 2 × atan(180 / 459.5) = 42.8°. Meta's docs say every stream size covers the
 * same view, scaled rather than cropped, so this holds for the 720-pixel
 * stream VISOR photographs from as well.
 */
object CameraOptics {

    /** Pixels of image shift per degree of head turn, at a 360-pixel-wide stream (lab V4). */
    const val PX_PER_DEG_AT_360 = 8.02

    private val FOCAL_PX_AT_360 = PX_PER_DEG_AT_360 * 180 / PI

    /** The camera stream's horizontal field of view, in degrees. */
    val HORIZONTAL_FOV_DEG: Double = 2 * atan(180 / FOCAL_PX_AT_360) * 180 / PI

    /**
     * The direction of pixel column [x] in a photo [widthPx] wide, in degrees
     * from the photo's centre: left is positive, as for head turns.
     */
    fun bearingInImage(x: Double, widthPx: Int): Double {
        val focal = (widthPx / 2.0) / tan(HORIZONTAL_FOV_DEG / 2 * PI / 180)
        return -atan((x - widthPx / 2.0) / focal) * 180 / PI
    }
}
