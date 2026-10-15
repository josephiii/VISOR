package ucf.visor.network;


import kotlinx.serialization.SerialName;
import kotlinx.serialization.Serializable;
import kotlin.collections.List

@Serializable
data class Hazard (
    @SerialName("classID") val id: Int,
    @SerialName("coords") val coords: List<Float>,
    @SerialName("depth") val depth: Float,
    @SerialName("object") val name: String,
    @SerialName("conf") val conf: Float,
)
