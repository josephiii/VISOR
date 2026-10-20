package ucf.visor.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One object the backend's hazard detection found in a photo ([HazardClient]).
 * What VISOR says about it is `SpokenPhrases.hazardsAhead`.
 */
@Serializable
data class Hazard(
    /** YOLO's class number for [name]. */
    @SerialName("classID") val id: Int,
    /** Its box in the photo, in pixels: x1, y1, x2, y2. */
    @SerialName("coords") val coords: List<Float>,
    /** How far away it is, in meters. */
    @SerialName("depth") val depth: Float,
    /** What it is, as YOLO names it: "person", "chair", "dining table"... */
    @SerialName("object") val name: String,
    /** How sure YOLO is, from 0 to 1. */
    @SerialName("conf") val conf: Float,
)
