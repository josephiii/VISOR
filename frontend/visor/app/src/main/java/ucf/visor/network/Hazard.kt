package ucf.visor.network;

import java.util.List;

import kotlinx.serialization.SerialName;
import kotlinx.serialization.Serializable;

@Serializable
data class Hazard (
    @SerialName("classID") val id: Int,
    @SerialName("coords") val coords: List<Float>,
    @SerialName("depth") val depth: Float,
    @SerialName("object") val name: String,
    @SerialName("conf") conf: Float,
)
