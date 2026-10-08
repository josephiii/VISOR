package ucf.visor.wearables

import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.ChargingState
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceType
import com.meta.wearable.dat.core.types.DonState
import com.meta.wearable.dat.core.types.HingeState
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.ThermalLevel

/**
 * What VISOR knows about a pair of glasses, from the device-state fields MWDAT
 * 1.0 added to `Device` (battery, charging, don, hinge, thermal).
 *
 * Every "unknown" from the SDK is carried as null rather than guessed: a
 * battery of 0 is what a pair reports before its control channel has warmed
 * up, not an empty battery (an empty pair would not be connected), so it reads
 * as unknown here.
 */
data class GlassesStatus(
    val name: String,
    val model: String,
    val linkState: LinkState,
    val batteryPercent: Int?,
    val charging: Boolean?,
    val worn: Boolean?,
    val hingeOpen: Boolean?,
    val thermal: ThermalLevel,
    val displayCapable: Boolean,
    val firmware: String?,
    val compatibility: DeviceCompatibility,
) {
    val connected: Boolean get() = linkState == LinkState.CONNECTED

    /** A plain-language temperature word, or null when the SDK does not know. */
    val temperatureWord: String?
        get() = when (thermal) {
            ThermalLevel.NONE, ThermalLevel.LIGHT -> "normal"
            ThermalLevel.MODERATE -> "warm"
            ThermalLevel.SEVERE -> "hot"
            ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY, ThermalLevel.SHUTDOWN -> "too hot"
            ThermalLevel.UNKNOWN -> null
        }

    /** Whether the glasses are hot enough that the SDK is already throttling. */
    val isThrottling: Boolean
        get() = thermal == ThermalLevel.MODERATE || thermal == ThermalLevel.SEVERE ||
            thermal == ThermalLevel.CRITICAL || thermal == ThermalLevel.EMERGENCY ||
            thermal == ThermalLevel.SHUTDOWN

    /**
     * One sentence per fact, in the order a wearer asks them, for TTS. Facts
     * the SDK does not know are left out rather than read as "unknown".
     */
    fun spokenSummary(): String = buildList {
        add(
            when (linkState) {
                LinkState.CONNECTED -> "Your $model glasses are connected."
                LinkState.CONNECTING -> "Your $model glasses are connecting."
                LinkState.DISCONNECTED -> "Your $model glasses are not connected."
            },
        )
        batteryPercent?.let { level ->
            add(
                when (charging) {
                    true -> "Battery $level percent, charging."
                    else -> "Battery $level percent."
                },
            )
        }
        when (worn) {
            true -> add("You are wearing them.")
            false -> add("They are not being worn.")
            null -> Unit
        }
        if (hingeOpen == false) add("They are folded.")
        temperatureWord?.let { add("Temperature $it.") }
        if (compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED) {
            add("They need a software update in the Meta AI app.")
        }
    }.joinToString(" ")

    /** Snapshot for a recording's metadata. */
    fun toMeta(): Map<String, Any?> = mapOf(
        "name" to name,
        "model" to model,
        "linkState" to linkState.name,
        "batteryPercent" to batteryPercent,
        "charging" to charging,
        "worn" to worn,
        "hingeOpen" to hingeOpen,
        "thermal" to thermal.name,
        "displayCapable" to displayCapable,
        "firmware" to firmware,
        "compatibility" to compatibility.name,
    )
}

fun Device.toGlassesStatus(): GlassesStatus = GlassesStatus(
    name = name.ifBlank { "Meta glasses" },
    model = deviceType.spokenName(),
    linkState = linkState,
    batteryPercent = batteryLevel.takeIf { it in 1..100 },
    charging = when (chargingState) {
        ChargingState.CHARGING -> true
        ChargingState.NOT_CHARGING -> false
        ChargingState.UNKNOWN -> null
    },
    worn = when (donState) {
        DonState.DONNED -> true
        DonState.DOFFED -> false
        DonState.UNKNOWN -> null
    },
    hingeOpen = when (hingeState) {
        HingeState.OPEN -> true
        HingeState.CLOSED -> false
        HingeState.UNKNOWN -> null
    },
    thermal = thermalLevel,
    displayCapable = isDisplayCapable(),
    firmware = firmwareInfo,
    compatibility = compatibility,
)

fun DeviceType.spokenName(): String = when (this) {
    DeviceType.RAYBAN_META -> "Ray-Ban Meta"
    DeviceType.META_RAYBAN_DISPLAY -> "Meta Ray-Ban Display"
    DeviceType.RAYBAN_META_OPTICS -> "Ray-Ban Meta Optics"
    DeviceType.OAKLEY_META_HSTN -> "Oakley Meta HSTN"
    DeviceType.OAKLEY_META_VANGUARD -> "Oakley Meta Vanguard"
    DeviceType.META_GLASSES -> "Meta"
    DeviceType.UNKNOWN -> "Meta"
}

/**
 * Which of several paired glasses VISOR means: the connected pair being worn,
 * then any connected pair, then one that is at least compatible. Lower is
 * better. A developer's phone often knows several pairs, of which one is on
 * the wearer's face — the one a session should open on.
 */
fun rankForSession(device: Device): Int = when {
    device.linkState == LinkState.CONNECTED && device.donState == DonState.DONNED -> 0
    device.linkState == LinkState.CONNECTED -> 1
    device.compatibility == DeviceCompatibility.COMPATIBLE -> 2
    else -> 3
}

/**
 * The best pair to open a session on right now, read synchronously from the
 * SDK's current device list. Requires `Wearables.initialize` to have run.
 */
fun preferredDevice(): Pair<DeviceIdentifier, Device>? =
    Wearables.devices.value
        .mapNotNull { id -> Wearables.devicesMetadata[id]?.value?.let { id to it } }
        .minByOrNull { rankForSession(it.second) }
