package ucf.visor.network;

import java.util.List;

import kotlinx.serialization.SerialName;
import kotlinx.serialization.Serializable;

@Serializable
data class Hazard {
    @SerialName("id") val id : Integer,
    @SerialName("coords") val coords: List<Float>
    @SerialName("depth") val depth: Integer
    @SerialName("name") name: String
    @SerialName("confidence") confidence: Int,
}
